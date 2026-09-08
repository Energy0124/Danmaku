use crate::library::{FolderEntry, LibraryCatalog, MediaItem, library_root_labels};
use crate::localization::{OrganizerText as T, Strings};
use eframe::egui;
use std::collections::{BTreeMap, BTreeSet};

#[derive(Default)]
pub(super) struct FolderSelection {
    pub enabled: bool,
    folders: BTreeSet<Vec<String>>,
    files: BTreeSet<String>,
    excluded: BTreeSet<String>,
    anchor: Option<String>,
    cache_version: Option<u64>,
    descendants: BTreeMap<Vec<String>, BTreeSet<String>>,
    resolved: BTreeSet<String>,
    dirty: bool,
    select_visible: bool,
}

pub(super) fn folder_ids(catalog: &LibraryCatalog, path: &[String]) -> BTreeSet<String> {
    let multi = library_root_labels(catalog).len() > 1;
    catalog
        .items
        .iter()
        .filter(|item| {
            let mut components = Vec::new();
            if multi {
                components.push(item.root_label.clone().unwrap_or_default());
            }
            components.extend(item.relative_path.split(['/', '\\']).map(str::to_owned));
            components.len() > path.len()
                && components
                    .iter()
                    .zip(path)
                    .all(|(a, b)| a.eq_ignore_ascii_case(b))
        })
        .map(|i| i.id.clone())
        .collect()
}

impl FolderSelection {
    pub fn resolved(&mut self, catalog: &LibraryCatalog, version: u64) -> &BTreeSet<String> {
        if self.cache_version != Some(version) {
            self.descendants.clear();
            let multi = library_root_labels(catalog).len() > 1;
            for item in &catalog.items {
                let mut path = Vec::new();
                if multi {
                    path.push(item.root_label.clone().unwrap_or_default());
                }
                let parts = item.relative_path.split(['/', '\\']).collect::<Vec<_>>();
                path.extend(
                    parts
                        .iter()
                        .take(parts.len().saturating_sub(1))
                        .map(|p| p.to_string()),
                );
                for length in 0..=path.len() {
                    self.descendants
                        .entry(path[..length].to_vec())
                        .or_default()
                        .insert(item.id.clone());
                }
            }
            self.cache_version = Some(version);
            self.dirty = true;
        }
        if self.dirty {
            self.resolved = self.files.clone();
            for folder in &self.folders {
                if let Some(ids) = self.descendants.get(folder) {
                    self.resolved.extend(ids.iter().cloned());
                }
            }
            self.resolved.retain(|id| !self.excluded.contains(id));
            self.dirty = false;
        }
        &self.resolved
    }

    pub fn toolbar(
        &mut self,
        ui: &mut egui::Ui,
        _catalog: &LibraryCatalog,
        _path: &[String],
        strings: Strings,
    ) {
        ui.toggle_value(&mut self.enabled, strings.organizer_text(T::Select));
        if self.enabled {
            if ui
                .button(strings.organizer_text(T::SelectVisible))
                .clicked()
            {
                self.select_visible = true;
            }
            if ui.button(strings.organizer_text(T::Clear)).clicked() {
                self.folders.clear();
                self.files.clear();
                self.excluded.clear();
                self.anchor = None;
                self.dirty = true;
            }
            ui.label(format!(
                "{} {} · {} {}",
                self.folders.len(),
                strings.organizer_text(T::Folders),
                self.resolved.len(),
                strings.organizer_text(T::SelectedVideos)
            ));
        }
    }

    pub fn rows(
        &mut self,
        ui: &mut egui::Ui,
        _catalog: &LibraryCatalog,
        path: &[String],
        folders: &[&FolderEntry],
        files: &[&MediaItem],
        strings: Strings,
        navigate: &mut Option<Option<String>>,
    ) {
        let keys = folders
            .iter()
            .map(|f| {
                format!(
                    "folder:{}",
                    path.iter()
                        .chain(std::iter::once(&f.name))
                        .cloned()
                        .collect::<Vec<_>>()
                        .join("/")
                )
            })
            .chain(files.iter().map(|f| format!("file:{}", f.id)))
            .collect::<Vec<_>>();
        let mut toggle = Vec::new();
        if self.select_visible {
            toggle.extend((0..keys.len()).map(|i| (i, true)));
            self.select_visible = false;
        }
        egui::ScrollArea::vertical()
            .id_salt("organizer-selection")
            .show_rows(ui, 28.0, keys.len(), |ui, range| {
                for index in range {
                    let (selected, label) = if index < folders.len() {
                        let folder = folders[index];
                        let mut full = path.to_vec();
                        full.push(folder.name.clone());
                        (
                            self.folders.contains(&full),
                            format!("{} ({})", folder.name, folder.item_count),
                        )
                    } else {
                        let file = files[index - folders.len()];
                        (self.resolved.contains(&file.id), file.relative_path.clone())
                    };
                    ui.horizontal(|ui| {
                        let mut checked = selected;
                        let checkbox = ui.checkbox(&mut checked, "");
                        let row = ui.selectable_label(selected, label);
                        if checkbox.changed() || row.clicked() {
                            let modifiers = ui.input(|i| i.modifiers);
                            if modifiers.shift {
                                let anchor = self
                                    .anchor
                                    .as_ref()
                                    .and_then(|a| keys.iter().position(|k| k == a))
                                    .unwrap_or(index);
                                toggle.extend(
                                    (anchor.min(index)..=anchor.max(index)).map(|i| (i, true)),
                                );
                            } else {
                                toggle.push((index, !selected));
                            }
                            self.anchor = Some(keys[index].clone());
                        }
                        if index < folders.len()
                            && ui
                                .small_button(strings.organizer_text(T::OpenFolder))
                                .clicked()
                        {
                            *navigate = Some(Some(folders[index].name.clone()));
                        }
                    });
                }
            });
        for (index, selected) in toggle {
            if index < folders.len() {
                let mut full = path.to_vec();
                full.push(folders[index].name.clone());
                if selected {
                    self.folders.insert(full);
                } else {
                    self.folders.remove(&full);
                }
            } else {
                let id = files[index - folders.len()].id.clone();
                if selected {
                    self.files.insert(id.clone());
                    self.excluded.remove(&id);
                } else {
                    self.files.remove(&id);
                    self.excluded.insert(id);
                }
            }
            self.dirty = true;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn overlapping_folders_deduplicate_and_file_exclusion_wins() {
        let catalog = LibraryCatalog {
            items: vec![
                MediaItem {
                    id: "one".into(),
                    relative_path: "A/B/one.mkv".into(),
                    ..Default::default()
                },
                MediaItem {
                    id: "two".into(),
                    relative_path: "A/two.mkv".into(),
                    ..Default::default()
                },
            ],
            ..Default::default()
        };
        let mut selection = FolderSelection::default();
        selection.folders.insert(vec!["A".into()]);
        selection.folders.insert(vec!["A".into(), "B".into()]);
        selection.excluded.insert("one".into());
        assert_eq!(
            selection.resolved(&catalog, 1),
            &BTreeSet::from(["two".into()])
        );
        assert_eq!(selection.cache_version, Some(1));
        assert!(!selection.dirty);
        assert_eq!(selection.resolved(&catalog, 1).len(), 1);
    }
}
