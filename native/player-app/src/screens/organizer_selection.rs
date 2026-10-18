//! Folder/file selection for organizing. Rules follow file managers and sync
//! clients: the most specific rule wins, so a folder can be selected while one
//! of its subfolders or files is left out, and folders show a partial state.
use crate::icons::{Icon, paint_icon};
use crate::library::{FolderEntry, LibraryCatalog, MediaItem, library_root_labels};
use crate::localization::{OrganizerText as T, Strings};
use crate::theme::{palette, typography};
use eframe::egui::{self, RichText};
use std::collections::{BTreeMap, BTreeSet};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Mark {
    None,
    Partial,
    All,
}

#[derive(Default)]
pub(super) struct FolderSelection {
    pub enabled: bool,
    /// Explicit rules: `true` includes, `false` excludes.
    folders: BTreeMap<Vec<String>, bool>,
    files: BTreeMap<String, bool>,
    anchor: Option<String>,
    cache_version: Option<u64>,
    /// Folder path of every catalog item, as browsed in Folders.
    item_folders: BTreeMap<String, Vec<String>>,
    resolved: BTreeSet<String>,
    dirty: bool,
}

fn browse_path(item: &MediaItem, multi_root: bool) -> Vec<String> {
    let mut path = Vec::new();
    if multi_root {
        path.push(item.root_label.clone().unwrap_or_default());
    }
    let parts = item.relative_path.split(['/', '\\']).collect::<Vec<_>>();
    path.extend(
        parts[..parts.len().saturating_sub(1)]
            .iter()
            .map(|p| (*p).to_owned()),
    );
    path
}

fn starts_with(path: &[String], prefix: &[String]) -> bool {
    path.len() >= prefix.len()
        && path
            .iter()
            .zip(prefix)
            .all(|(a, b)| a.eq_ignore_ascii_case(b))
}

/// IDs of every catalogued video below `path`.
pub(super) fn folder_ids(catalog: &LibraryCatalog, path: &[String]) -> BTreeSet<String> {
    let multi = library_root_labels(catalog).len() > 1;
    catalog
        .items
        .iter()
        .filter(|item| starts_with(&browse_path(item, multi), path))
        .map(|i| i.id.clone())
        .collect()
}

impl FolderSelection {
    fn refresh(&mut self, catalog: &LibraryCatalog, version: u64) {
        if self.cache_version != Some(version) {
            let multi = library_root_labels(catalog).len() > 1;
            self.item_folders = catalog
                .items
                .iter()
                .map(|item| (item.id.clone(), browse_path(item, multi)))
                .collect();
            self.cache_version = Some(version);
            self.dirty = true;
        }
        if self.dirty {
            self.resolved = self
                .item_folders
                .iter()
                .filter(|(id, folder)| self.included(id, folder))
                .map(|(id, _)| id.clone())
                .collect();
            self.dirty = false;
        }
    }

    /// The most specific rule for a video decides: its own, then its deepest folder.
    fn included(&self, id: &str, folder: &[String]) -> bool {
        if let Some(rule) = self.files.get(id) {
            return *rule;
        }
        self.folder_rule(folder).unwrap_or(false)
    }

    fn folder_rule(&self, folder: &[String]) -> Option<bool> {
        (0..=folder.len())
            .rev()
            .find_map(|length| self.folders.get(&folder[..length]).copied())
    }

    pub fn resolved(&mut self, catalog: &LibraryCatalog, version: u64) -> &BTreeSet<String> {
        self.refresh(catalog, version);
        &self.resolved
    }

    pub fn mark(&self, folder: &[String]) -> Mark {
        let mut any = false;
        let mut all = true;
        for (id, path) in &self.item_folders {
            if starts_with(path, folder) {
                if self.resolved.contains(id) {
                    any = true;
                } else {
                    all = false;
                }
            }
        }
        match (any, all) {
            (true, true) => Mark::All,
            (true, false) => Mark::Partial,
            _ => Mark::None,
        }
    }

    pub fn set_folder(&mut self, folder: &[String], selected: bool) {
        // A folder decision replaces the decisions made inside it.
        self.folders.retain(|path, _| !starts_with(path, folder));
        let inside = self
            .item_folders
            .iter()
            .filter(|(_, path)| starts_with(path, folder))
            .map(|(id, _)| id.clone())
            .collect::<BTreeSet<_>>();
        self.files.retain(|id, _| !inside.contains(id));
        if self.folder_rule(folder).unwrap_or(false) != selected {
            self.folders.insert(folder.to_vec(), selected);
        }
        self.dirty = true;
    }

    pub fn set_file(&mut self, id: &str, selected: bool) {
        let inherited = self
            .item_folders
            .get(id)
            .and_then(|folder| self.folder_rule(folder))
            .unwrap_or(false);
        if inherited == selected {
            self.files.remove(id);
        } else {
            self.files.insert(id.to_owned(), selected);
        }
        self.dirty = true;
    }

    pub fn clear(&mut self) {
        self.folders.clear();
        self.files.clear();
        self.anchor = None;
        self.dirty = true;
    }

    /// Selection toolbar shown in Folders while selecting.
    pub fn toolbar(
        &mut self,
        ui: &mut egui::Ui,
        path: &[String],
        folders: &[&FolderEntry],
        files: &[&MediaItem],
        strings: Strings,
    ) {
        if ui
            .button(strings.organizer_text(T::SelectVisible))
            .clicked()
        {
            for folder in folders {
                let mut full = path.to_vec();
                full.push(folder.name.clone());
                self.set_folder(&full, true);
            }
            for file in files {
                self.set_file(&file.id, true);
            }
        }
        if ui
            .button(strings.organizer_text(T::ClearSelection))
            .clicked()
        {
            self.clear();
        }
        ui.label(
            RichText::new(strings.organizer_selected_videos(self.resolved.len()))
                .color(palette::TEXT_SECONDARY),
        );
    }

    /// Checkbox rows. Clicking a folder name opens it; checkboxes select.
    #[allow(clippy::too_many_arguments)]
    pub fn rows(
        &mut self,
        ui: &mut egui::Ui,
        path: &[String],
        folders: &[&FolderEntry],
        files: &[&MediaItem],
        strings: Strings,
        navigate: &mut Option<Option<String>>,
        gutter: f32,
    ) {
        let keys = folders
            .iter()
            .map(|f| format!("folder:{}", f.name))
            .chain(files.iter().map(|f| format!("file:{}", f.id)))
            .collect::<Vec<_>>();
        let mut toggles = Vec::<(usize, bool)>::new();
        let shift = ui.input(|i| i.modifiers.shift);
        for index in 0..keys.len() {
            let (mark, name, count) = if index < folders.len() {
                let folder = folders[index];
                let mut full = path.to_vec();
                full.push(folder.name.clone());
                (
                    self.mark(&full),
                    folder.name.clone(),
                    Some(folder.item_count),
                )
            } else {
                let file = files[index - folders.len()];
                let mark = if self.resolved.contains(&file.id) {
                    Mark::All
                } else {
                    Mark::None
                };
                let name = file
                    .relative_path
                    .rsplit(['/', '\\'])
                    .next()
                    .unwrap_or(&file.relative_path)
                    .to_owned();
                (mark, name, None)
            };
            let width = ui.available_width() - 2.0 * gutter;
            let (rect, response) = ui
                .allocate_exact_size(egui::vec2(width + 2.0 * gutter, 40.0), egui::Sense::click());
            if !ui.is_rect_visible(rect) {
                continue;
            }
            let row = egui::Rect::from_min_size(
                rect.min + egui::vec2(gutter, 2.0),
                egui::vec2(width, 36.0),
            );
            let fill = match mark {
                Mark::All => palette::WIDGET_ACTIVE,
                _ if response.hovered() => palette::WIDGET_HOVER,
                Mark::Partial => palette::SURFACE_RAISED,
                Mark::None => palette::SURFACE,
            };
            ui.painter().rect_filled(row, 6.0, fill);
            let mut checked = mark == Mark::All;
            let checkbox = ui.put(
                egui::Rect::from_center_size(
                    egui::pos2(row.left() + 20.0, row.center().y),
                    egui::vec2(22.0, 22.0),
                ),
                egui::Checkbox::without_text(&mut checked).indeterminate(mark == Mark::Partial),
            );
            let mut text_left = row.left() + 42.0;
            if count.is_some() {
                paint_icon(
                    ui.painter(),
                    egui::Rect::from_center_size(
                        egui::pos2(text_left + 9.0, row.center().y),
                        egui::vec2(18.0, 18.0),
                    ),
                    Icon::Folder,
                    palette::TEXT_SECONDARY,
                    1.5,
                );
                text_left += 26.0;
            }
            let painter = ui.painter_at(row.shrink2(egui::vec2(0.0, 0.0)));
            painter.text(
                egui::pos2(text_left, row.center().y),
                egui::Align2::LEFT_CENTER,
                name,
                typography::body(),
                if mark == Mark::None {
                    palette::TEXT_SECONDARY
                } else {
                    palette::TEXT_PRIMARY
                },
            );
            if let Some(count) = count {
                painter.text(
                    egui::pos2(row.right() - 12.0, row.center().y),
                    egui::Align2::RIGHT_CENTER,
                    format!("{count} {}", strings.items_label()),
                    typography::caption(),
                    palette::TEXT_MUTED,
                );
            }
            if checkbox.clicked() {
                toggles.push((index, mark != Mark::All));
            } else if response.clicked() {
                if index < folders.len() {
                    *navigate = Some(Some(folders[index].name.clone()));
                } else {
                    toggles.push((index, mark != Mark::All));
                }
            }
            if index < folders.len() {
                response.on_hover_text(strings.organizer_text(T::OpenFolderHint));
            }
        }
        for (index, selected) in toggles {
            let range = match (
                shift,
                self.anchor
                    .as_ref()
                    .and_then(|a| keys.iter().position(|k| k == a)),
            ) {
                (true, Some(anchor)) => anchor.min(index)..=anchor.max(index),
                _ => index..=index,
            };
            for item in range {
                if item < folders.len() {
                    let mut full = path.to_vec();
                    full.push(folders[item].name.clone());
                    self.set_folder(&full, selected);
                } else {
                    self.set_file(&files[item - folders.len()].id, selected);
                }
            }
            self.anchor = Some(keys[index].clone());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn catalog() -> LibraryCatalog {
        let item = |id: &str, path: &str| MediaItem {
            id: id.into(),
            relative_path: path.into(),
            ..Default::default()
        };
        LibraryCatalog {
            items: vec![
                item("one", "A/B/one.mkv"),
                item("two", "A/two.mkv"),
                item("three", "A/C/three.mkv"),
                item("four", "D/four.mkv"),
            ],
            ..Default::default()
        }
    }

    fn path(parts: &[&str]) -> Vec<String> {
        parts.iter().map(|p| (*p).to_owned()).collect()
    }

    #[test]
    fn subfolders_and_files_can_be_left_out_of_a_selected_folder() {
        let catalog = catalog();
        let mut selection = FolderSelection::default();
        selection.resolved(&catalog, 1);
        selection.set_folder(&path(&["A"]), true);
        selection.set_folder(&path(&["A", "B"]), false);
        assert_eq!(
            selection.resolved(&catalog, 1),
            &BTreeSet::from(["two".into(), "three".into()])
        );
        assert_eq!(selection.mark(&path(&["A"])), Mark::Partial);
        assert_eq!(selection.mark(&path(&["A", "B"])), Mark::None);
        assert_eq!(selection.mark(&path(&["A", "C"])), Mark::All);
        // The most specific rule wins: a file inside an excluded folder.
        selection.set_file("one", true);
        assert!(selection.resolved(&catalog, 1).contains("one"));
        selection.set_file("three", false);
        assert!(!selection.resolved(&catalog, 1).contains("three"));
        // Re-selecting the parent replaces every rule inside it.
        selection.set_folder(&path(&["A"]), true);
        assert_eq!(selection.resolved(&catalog, 1).len(), 3);
        assert_eq!(selection.mark(&path(&["A"])), Mark::All);
        selection.set_folder(&path(&["A"]), false);
        assert!(selection.resolved(&catalog, 1).is_empty());
        assert!(selection.folders.is_empty() && selection.files.is_empty());
    }

    #[test]
    fn folder_ids_cover_every_video_below_a_folder() {
        assert_eq!(
            folder_ids(&catalog(), &path(&["A"])),
            BTreeSet::from(["one".into(), "two".into(), "three".into()])
        );
    }
}
