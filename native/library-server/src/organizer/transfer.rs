//! Journaled transfers. Identity checks distinguish our files from racing writers.
use super::*;
use sha2::{Digest, Sha256};
use std::io::{Read, Write};

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(super) struct TransferRecord {
    pub digest: String,
    pub source_identity: String,
    pub stage: Option<PathBuf>,
    pub stage_identity: Option<String>,
    pub publish_intent: bool,
    pub removed: bool,
    pub restore_stage: Option<PathBuf>,
    pub restore_identity: Option<String>,
}

pub(super) fn digest(path: &Path) -> Result<String> {
    let mut input = fs::File::open(path)?;
    let mut hash = Sha256::new();
    let mut buffer = vec![0; 1024 * 1024];
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hash.update(&buffer[..count]);
    }
    Ok(hash
        .finalize()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect())
}

pub(super) fn signature(path: &Path) -> Result<String> {
    let metadata = fs::metadata(path)?;
    let modified = metadata
        .modified()?
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|e| LibraryServerError::new(e.to_string()))?
        .as_nanos();
    Ok(format!("{}:{}:{modified}", identity(path)?, metadata.len()))
}

#[cfg(windows)]
fn identity(path: &Path) -> Result<String> {
    use std::os::windows::io::AsRawHandle;
    use windows_sys::Win32::Storage::FileSystem::{
        BY_HANDLE_FILE_INFORMATION, GetFileInformationByHandle,
    };
    let file = fs::File::open(path)?;
    let mut info: BY_HANDLE_FILE_INFORMATION = unsafe { std::mem::zeroed() };
    if unsafe { GetFileInformationByHandle(file.as_raw_handle(), &mut info) } == 0 {
        return Err(std::io::Error::last_os_error().into());
    }
    Ok(format!(
        "{}:{}:{}",
        info.dwVolumeSerialNumber, info.nFileIndexHigh, info.nFileIndexLow
    ))
}

#[cfg(unix)]
fn identity(path: &Path) -> Result<String> {
    use std::os::unix::fs::MetadataExt;
    let metadata = fs::metadata(path)?;
    Ok(format!("{}:{}", metadata.dev(), metadata.ino()))
}

fn existing_parent(path: &Path) -> Result<&Path> {
    path.ancestors()
        .find(|p| p.exists())
        .ok_or_else(|| LibraryServerError::new("The drive is unavailable."))
}

#[cfg(windows)]
fn volume(path: &Path) -> Result<String> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::GetVolumePathNameW;
    let wide = existing_parent(path)?
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let mut output = vec![0u16; 32768];
    if unsafe { GetVolumePathNameW(wide.as_ptr(), output.as_mut_ptr(), output.len() as u32) } == 0 {
        return Err(std::io::Error::last_os_error().into());
    }
    Ok(String::from_utf16_lossy(
        &output[..output.iter().position(|c| *c == 0).unwrap_or(output.len())],
    )
    .to_lowercase())
}

#[cfg(unix)]
fn volume(path: &Path) -> Result<String> {
    use std::os::unix::fs::MetadataExt;
    Ok(fs::metadata(existing_parent(path)?)?.dev().to_string())
}

#[cfg(windows)]
fn free_space(path: &Path) -> Result<u64> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Storage::FileSystem::GetDiskFreeSpaceExW;
    let wide = existing_parent(path)?
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let mut available = 0;
    if unsafe {
        GetDiskFreeSpaceExW(
            wide.as_ptr(),
            &mut available,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
        )
    } == 0
    {
        return Err(std::io::Error::last_os_error().into());
    }
    Ok(available)
}

#[cfg(unix)]
fn free_space(path: &Path) -> Result<u64> {
    use std::os::unix::ffi::OsStrExt;
    let path = std::ffi::CString::new(existing_parent(path)?.as_os_str().as_bytes())
        .map_err(|e| LibraryServerError::new(e.to_string()))?;
    let mut stat: libc::statvfs = unsafe { std::mem::zeroed() };
    if unsafe { libc::statvfs(path.as_ptr(), &mut stat) } != 0 {
        return Err(std::io::Error::last_os_error().into());
    }
    Ok((stat.f_bavail as u64).saturating_mul(stat.f_frsize as u64))
}

fn matches(path: &Path, expected_identity: &str, expected_digest: &str) -> Result<bool> {
    Ok(path.is_file() && identity(path)? == expected_identity && digest(path)? == expected_digest)
}

fn copy_chunks(
    source: &Path,
    output: &mut fs::File,
    cancel: &AtomicBool,
    mut progress: impl FnMut(u64),
) -> Result<()> {
    let mut input = fs::File::open(source)?;
    let mut buffer = vec![0; 1024 * 1024];
    loop {
        if cancel.load(Ordering::Acquire) {
            return Err(LibraryServerError::new("Organization cancelled."));
        }
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        output.write_all(&buffer[..count])?;
        progress(count as u64);
    }
    output.sync_all()?;
    Ok(())
}

fn staging_path(destination: &Path) -> Result<PathBuf> {
    Ok(destination.with_file_name(format!(
        ".danmaku-transfer-{}.tmp",
        super::draft::unique_id()?
    )))
}

impl LibraryOrganizer {
    pub(super) fn execute_transfer(&self, batch: &StoredBatch, undo: bool) -> Result<()> {
        preflight_batch(batch)?;
        let mut records = Vec::new();
        let mut required = BTreeMap::<String, (PathBuf, u64)>::new();
        for operation in &batch.moves {
            let source = operation.source(&batch.root);
            let destination = operation.destination(&batch.root);
            self.validate_operation(operation, &batch.root)?;
            if operation
                .source_signature
                .as_ref()
                .is_some_and(|expected| signature(&source).ok().as_ref() != Some(expected))
            {
                return Err(LibraryServerError::new(
                    "The source changed after approval; review it again.",
                ));
            }
            let cross_volume = volume(&source)? != volume(&destination)?;
            #[cfg(test)]
            let cross_volume = cross_volume || self.force_copy.load(Ordering::Relaxed);
            let content_hash = digest(&source)?;
            if let Some(expected) = &operation.content_hash {
                if expected != &content_hash {
                    return Err(LibraryServerError::new(
                        "The file changed since its last move; undo is blocked.",
                    ));
                }
            }
            if cross_volume {
                let entry = required
                    .entry(volume(&destination)?)
                    .or_insert((destination.clone(), 0));
                entry.1 = entry
                    .1
                    .checked_add(operation.size_bytes)
                    .ok_or_else(|| LibraryServerError::new("Transfer size overflow."))?;
            }
            records.push(TransferRecord {
                digest: content_hash,
                source_identity: identity(&source)?,
                stage: if cross_volume {
                    Some(staging_path(&destination)?)
                } else {
                    None
                },
                ..Default::default()
            });
        }
        for (_, (path, bytes)) in required {
            let available = free_space(&path)?;
            #[cfg(test)]
            let available = self
                .available_space
                .lock()
                .expect("test space")
                .unwrap_or(available);
            if available < bytes {
                return Err(LibraryServerError::new(
                    "The destination drive does not have enough free space.",
                ));
            }
        }
        let mut transaction = JournalTransaction {
            batch: batch.clone(),
            moved_count: 0,
            undo,
            transfers: records,
            catalog_committed: false,
            catalog_commit_revision: None,
        };
        for (operation, record) in transaction
            .batch
            .moves
            .iter_mut()
            .zip(&transaction.transfers)
        {
            operation.content_hash = Some(record.digest.clone());
        }
        self.save_transaction(&transaction)?;
        self.transfer_checkpoint("journal")?;

        // All cross-drive copies are verified before the first original is removed.
        for index in 0..batch.moves.len() {
            let operation = &batch.moves[index];
            self.validate_operation(operation, &batch.root)?;
            if let Some(stage) = transaction.transfers[index].stage.clone() {
                fs::create_dir_all(stage.parent().unwrap())?;
                let mut output = fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&stage)?;
                transaction.transfers[index].stage_identity = Some(identity(&stage)?);
                self.save_transaction(&transaction)?;
                self.transfer_checkpoint("staged")?;
                copy_chunks(
                    &operation.source(&batch.root),
                    &mut output,
                    &self.cancel_requested,
                    |bytes| {
                        self.runtime
                            .lock()
                            .expect("organizer lock")
                            .status
                            .completed_bytes += bytes;
                    },
                )?;
                drop(output);
                let record = &transaction.transfers[index];
                if digest(&stage)? != record.digest
                    || !matches(
                        &operation.source(&batch.root),
                        &record.source_identity,
                        &record.digest,
                    )?
                {
                    return Err(LibraryServerError::new(
                        "Source changed or copied content verification failed.",
                    ));
                }
                self.save_transaction(&transaction)?;
                self.transfer_checkpoint("verified")?;
            }
        }
        for index in 0..batch.moves.len() {
            if self.cancel_requested.load(Ordering::Acquire) {
                return Err(LibraryServerError::new("Organization cancelled."));
            }
            let operation = &batch.moves[index];
            self.validate_operation(operation, &batch.root)?;
            let source = operation.source(&batch.root);
            let destination = operation.destination(&batch.root);
            let record = &transaction.transfers[index];
            if !matches(&source, &record.source_identity, &record.digest)? {
                return Err(LibraryServerError::new(
                    "Source changed during organization.",
                ));
            }
            fs::create_dir_all(destination.parent().unwrap())?;
            transaction.transfers[index].publish_intent = true;
            self.save_transaction(&transaction)?;
            self.transfer_checkpoint("publish-intent")?;
            let record = &transaction.transfers[index];
            if let Some(stage) = &record.stage {
                if !matches(
                    stage,
                    record.stage_identity.as_deref().unwrap_or_default(),
                    &record.digest,
                )? {
                    return Err(LibraryServerError::new("Staged file changed."));
                }
                move_without_overwrite(stage, &destination)?;
                self.save_transaction(&transaction)?;
                self.transfer_checkpoint("published")?;
                if !matches(&source, &record.source_identity, &record.digest)? {
                    return Err(LibraryServerError::new("Source changed before removal."));
                }
                // Source deletion is authorized only after a verified copy was published.
                fs::remove_file(&source)?;
            } else {
                move_without_overwrite(&source, &destination)?;
                self.transfer_checkpoint("published")?;
                self.runtime
                    .lock()
                    .expect("organizer lock")
                    .status
                    .completed_bytes += operation.size_bytes;
            }
            transaction.transfers[index].removed = true;
            transaction.moved_count = index + 1;
            self.save_transaction(&transaction)?;
            self.transfer_checkpoint("removed")?;
            self.runtime
                .lock()
                .expect("organizer lock")
                .status
                .completed_operations = index + 1;
        }
        Ok(())
    }

    fn validate_operation(&self, operation: &OrganizationMove, legacy_root: &Path) -> Result<()> {
        for (root, path) in [
            (
                operation.source_root.as_deref().unwrap_or(legacy_root),
                operation.source(legacy_root),
            ),
            (
                operation.destination_root.as_deref().unwrap_or(legacy_root),
                operation.destination(legacy_root),
            ),
        ] {
            if !self.roots.iter().any(|r| paths_equal(r, root)) {
                return Err(LibraryServerError::new(
                    "A transfer root is no longer configured.",
                ));
            }
            ensure_within_root(&normalize_absolute(root)?, &normalize_absolute(&path)?)?;
            reject_reparse_ancestors(root, &path)?;
        }
        Ok(())
    }

    fn save_transaction(&self, transaction: &JournalTransaction) -> Result<()> {
        self.runtime.lock().expect("organizer lock").journal.active = Some(transaction.clone());
        self.persist_journal()
    }

    pub(super) fn transfer_checkpoint(&self, phase: &str) -> Result<()> {
        #[cfg(test)]
        if self
            .transfer_failpoint
            .lock()
            .expect("test failpoint")
            .as_deref()
            == Some(phase)
        {
            return Err(LibraryServerError::new(format!(
                "Injected interruption at {phase}"
            )));
        }
        let _ = phase;
        Ok(())
    }

    pub(super) fn rollback_transfer(&self, mut transaction: JournalTransaction) -> Result<()> {
        rollback(&mut transaction, |transaction| {
            self.save_transaction(transaction)
        })
    }
}

pub(super) fn rollback(
    transaction: &mut JournalTransaction,
    mut persist: impl FnMut(&JournalTransaction) -> Result<()>,
) -> Result<()> {
    for index in (0..transaction.batch.moves.len()).rev() {
        let operation = transaction.batch.moves[index].clone();
        let root = &transaction.batch.root;
        let source = operation.source(root);
        let destination = operation.destination(root);
        // Never create directories on a disconnected mount or removed root.
        for root in [
            operation.source_root.as_deref().unwrap_or(root),
            operation.destination_root.as_deref().unwrap_or(root),
        ] {
            if !root.is_dir() {
                return Err(LibraryServerError::new(
                    "Reconnect both transfer drives before retrying recovery.",
                ));
            }
        }
        reject_reparse_ancestors(operation.source_root.as_deref().unwrap_or(root), &source)?;
        reject_reparse_ancestors(
            operation.destination_root.as_deref().unwrap_or(root),
            &destination,
        )?;
        let record = transaction.transfers[index].clone();
        let destination_identity = record
            .stage_identity
            .as_deref()
            .unwrap_or(&record.source_identity);
        let our_destination =
            record.publish_intent && matches(&destination, destination_identity, &record.digest)?;
        if record.publish_intent
            && destination.exists()
            && !our_destination
            && (record.removed || record.stage.as_ref().is_none_or(|stage| !stage.exists()))
        {
            return Err(LibraryServerError::new(
                "The published destination changed; recovery will not remove it.",
            ));
        }
        if !source.exists() {
            if !our_destination {
                return Err(LibraryServerError::new(
                    "Cannot find the verified moved file for recovery.",
                ));
            }
            if record.stage.is_none() {
                move_without_overwrite(&destination, &source)?;
            } else {
                if free_space(&source)? < operation.size_bytes {
                    return Err(LibraryServerError::new(
                        "Insufficient space to restore the original.",
                    ));
                }
                fs::create_dir_all(source.parent().unwrap())?;
                let stage = match record.restore_stage.clone() {
                    Some(stage) => stage,
                    None => staging_path(&source)?,
                };
                if stage.exists() {
                    if record
                        .restore_identity
                        .as_ref()
                        .is_none_or(|id| identity(&stage).ok().as_ref() != Some(id))
                    {
                        return Err(LibraryServerError::new(
                            "Recovery staging file is not owned by this operation.",
                        ));
                    }
                    fs::remove_file(&stage)?;
                }
                transaction.transfers[index].restore_stage = Some(stage.clone());
                persist(transaction)?;
                let mut output = fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&stage)?;
                transaction.transfers[index].restore_identity = Some(identity(&stage)?);
                persist(transaction)?;
                copy_chunks(&destination, &mut output, &AtomicBool::new(false), |_| {})?;
                drop(output);
                if digest(&stage)? != record.digest {
                    return Err(LibraryServerError::new(
                        "Restored content verification failed.",
                    ));
                }
                move_without_overwrite(&stage, &source)?;
                persist(transaction)?;
            }
        }
        let record = &transaction.transfers[index];
        let source_is_original = matches(&source, &record.source_identity, &record.digest)?;
        let source_is_restored = if let Some(id) = &record.restore_identity {
            matches(&source, id, &record.digest)?
        } else {
            false
        };
        if !source_is_original && !source_is_restored {
            return Err(LibraryServerError::new(
                "Original path is occupied or its content changed; recovery requires review.",
            ));
        }
        if our_destination && destination.exists() {
            fs::remove_file(&destination)?;
        }
        if let Some(stage) = &record.stage {
            if stage.exists() {
                if record
                    .stage_identity
                    .as_ref()
                    .is_none_or(|id| identity(stage).ok().as_ref() != Some(id))
                {
                    return Err(LibraryServerError::new(
                        "Transfer staging file requires review.",
                    ));
                }
                fs::remove_file(stage)?;
            }
        }
        transaction.transfers[index].removed = false;
        persist(transaction)?;
    }
    Ok(())
}
