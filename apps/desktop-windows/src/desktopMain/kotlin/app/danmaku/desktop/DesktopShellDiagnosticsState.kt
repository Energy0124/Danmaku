package app.danmaku.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import app.danmaku.server.LocalLibraryServerEvent

internal class DesktopShellDiagnosticsState(
    internal val fileLog: DesktopDiagnosticFileLog,
) {
    internal val mpvCommandLog = mutableStateListOf<DesktopMpvCommand>()
    internal val diagnosticLog = mutableStateListOf<DesktopDiagnosticLogEntry>()
    internal val serverEvents = mutableStateListOf<LocalLibraryServerEvent>()

    internal fun appendDiagnostic(category: String, message: String) {
        val entry = DesktopDiagnosticLogEntry(
            occurredAtEpochMs = System.currentTimeMillis(),
            category = category,
            message = message.redactToken(),
        )
        diagnosticLog += entry
        fileLog.append(entry.occurredAtEpochMs, entry.category, entry.message)
        trimDiagnosticLog()
    }

    internal fun appendServerEvent(event: LocalLibraryServerEvent) {
        serverEvents += event
        while (serverEvents.size > MAX_SERVER_DASHBOARD_EVENTS) {
            serverEvents.removeAt(0)
        }
        val entry = DesktopDiagnosticLogEntry(
            occurredAtEpochMs = event.occurredAtEpochMs,
            category = "server:${event.category}",
            message = "${event.method} ${event.path} -> ${event.status} ${event.detail}".redactToken(),
        )
        diagnosticLog += entry
        fileLog.append(entry.occurredAtEpochMs, entry.category, entry.message)
        trimDiagnosticLog()
    }

    private fun trimDiagnosticLog() {
        while (diagnosticLog.size > MAX_DIAGNOSTIC_LOG_ENTRIES) {
            diagnosticLog.removeAt(0)
        }
    }
}

@Composable
internal fun rememberDesktopShellDiagnosticsState(): DesktopShellDiagnosticsState =
    remember {
        DesktopShellDiagnosticsState(
            fileLog = DesktopDiagnosticFileLog.createDefault(),
        )
    }
