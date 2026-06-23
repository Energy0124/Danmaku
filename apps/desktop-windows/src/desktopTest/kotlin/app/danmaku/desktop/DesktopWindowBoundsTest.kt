package app.danmaku.desktop

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowBoundsTest {
    @Test
    fun keepsRestoreBoundsWhenScaleIsUnchanged() {
        assertEquals(
            Rectangle(81, 72, 1588, 954),
            Rectangle(81, 72, 1588, 954).scaledForRestoreCoordinateSpace(1.0, 1.0),
        )
    }

    @Test
    fun scalesSavedAwtBoundsForHighDpiRestore() {
        assertEquals(
            Rectangle(72, 72, 2400, 1440),
            Rectangle(48, 48, 1600, 960).scaledForRestoreCoordinateSpace(1.5, 1.5),
        )
    }

    @Test
    fun clampsScaledRestoreBoundsAgainstScaledScreenBounds() {
        val scaledAvailableBounds = Rectangle(0, 0, 2560, 1440).scaledForRestoreCoordinateSpace(1.5, 1.5)

        assertEquals(
            Rectangle(72, 72, 2400, 1440),
            Rectangle(48, 48, 1600, 960)
                .scaledForRestoreCoordinateSpace(1.5, 1.5)
                .clampedToScreenAvailableBounds(scaledAvailableBounds),
        )
    }

    @Test
    fun keepsRestoreBoundsInsideAvailableScreen() {
        assertEquals(
            Rectangle(81, 72, 1588, 954),
            Rectangle(81, 72, 1588, 954).clampedToScreenAvailableBounds(Rectangle(0, 0, 2560, 1440)),
        )
    }

    @Test
    fun clampsRestoreBoundsThatWouldExtendPastScreenEdges() {
        assertEquals(
            Rectangle(54, 48, 2506, 1392),
            Rectangle(81, 72, 2506, 1392).clampedToScreenAvailableBounds(Rectangle(0, 0, 2560, 1440)),
        )
    }

    @Test
    fun shrinksOversizedRestoreBoundsToAvailableScreen() {
        assertEquals(
            Rectangle(0, 0, 2560, 1440),
            Rectangle(81, 72, 3000, 1800).clampedToScreenAvailableBounds(Rectangle(0, 0, 2560, 1440)),
        )
    }

    @Test
    fun normalizesInvalidRestoreSizeWhenScreenUnavailable() {
        assertEquals(
            Rectangle(10, 20, 1, 1),
            Rectangle(10, 20, 0, -4).clampedToScreenAvailableBounds(null),
        )
    }
}
