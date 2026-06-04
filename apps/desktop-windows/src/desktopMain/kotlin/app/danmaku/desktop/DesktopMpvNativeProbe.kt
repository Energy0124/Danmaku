package app.danmaku.desktop

object DesktopMpvNativeProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val runtime = DesktopMpvCommandExecutorRuntimeFactory().create {}
        runtime.use {
            println(it.statusMessage)
            check(it.mode == DesktopMpvCommandExecutorMode.NATIVE) {
                "Native libmpv executor did not start."
            }
        }
    }
}
