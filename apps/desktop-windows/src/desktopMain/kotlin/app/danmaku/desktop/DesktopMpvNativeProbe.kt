package app.danmaku.desktop

import java.nio.file.Files
import java.nio.file.Path

object DesktopMpvNativeProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val mediaPath = args.firstOrNull()?.let(Path::of)
        val runtime = DesktopMpvCommandExecutorRuntimeFactory().create(
            nativeOptions = mapOf("config" to "no"),
        ) {}
        runtime.use {
            println(it.statusMessage)
            check(it.mode == DesktopMpvCommandExecutorMode.NATIVE) {
                "Native libmpv executor did not start."
            }
            mediaPath?.let { path ->
                check(Files.isRegularFile(path)) {
                    "Probe media file does not exist: ${path.toAbsolutePath().normalize()}"
                }
                it.executor.execute(
                    DesktopMpvCommand(
                        listOf(
                            "loadfile",
                            path.toAbsolutePath().normalize().toString(),
                            "replace",
                        ),
                    ),
                )
                it.executor.execute(DesktopMpvCommand(listOf("set", "pause", "yes")))
                println("Loaded probe media: ${path.toAbsolutePath().normalize()}")
            }
        }
    }
}
