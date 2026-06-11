package app.danmaku.desktop

import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal fun selectLibraryDirectory(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = title
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

internal fun selectMediaFile(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(
            "Video files",
            "mkv",
            "mp4",
            "m4v",
            "avi",
            "mov",
            "webm",
            "ts",
            "m2ts",
        )
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }

internal fun selectDanmakuFile(title: String) =
    JFileChooser().run {
        fileSelectionMode = JFileChooser.FILES_ONLY
        dialogTitle = title
        fileFilter = FileNameExtensionFilter(
            "Danmaku files",
            "xml",
            "json",
            "danmaku",
        )
        takeIf { showOpenDialog(null) == JFileChooser.APPROVE_OPTION }
            ?.selectedFile
            ?.toPath()
    }
