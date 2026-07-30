package app.danmaku.tv.benchmark

import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope

internal const val TARGET_PACKAGE = "app.danmaku.tv"
private const val QA_FIXTURE_EXTRA = "app.danmaku.tv.QA_FIXTURE"

internal fun MacrobenchmarkScope.startFixtureAndWait() {
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN)
            .setPackage(TARGET_PACKAGE)
            .setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.MainActivity")
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(QA_FIXTURE_EXTRA, true),
    )
}

internal fun MacrobenchmarkScope.openLibraryDetailAndPlayer() {
    device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
    device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
    device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
    device.waitForIdle()
    device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
    device.waitForIdle()
    device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.traverseOneHundredActions() {
    val actions = buildList {
        // Player audio, subtitle, and danmaku overlays.
        addAll(
            listOf(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_CENTER,
            ),
        )
        repeat(5) {
            add(KeyEvent.KEYCODE_DPAD_DOWN)
            add(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        // Back to detail and Library, exercise filters, then visit every top-level route.
        addAll(
            listOf(
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_CENTER,
            ),
        )
        while (size < 100) {
            addAll(
                listOf(
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_UP,
                ).take(100 - size),
            )
        }
    }
    check(actions.size == 100)
    actions.forEach(device::pressKeyCode)
    device.waitForIdle()
}
