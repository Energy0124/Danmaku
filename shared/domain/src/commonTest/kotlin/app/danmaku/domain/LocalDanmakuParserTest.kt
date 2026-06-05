package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDanmakuParserTest {
    @Test
    fun parsesBilibiliXmlDanmaku() {
        val events = LocalDanmakuParser.parseBilibiliXml(
            """
            <i>
              <d p="1.250,1,25,16777215,0,0,user,row-1">hello &amp; world</d>
              <d p="2.500,5,18,16711680,0,0,user,row-2">top comment</d>
              <d p="3.750,4,36,255,0,0,user,row-3">bottom comment</d>
            </i>
            """.trimIndent(),
        )

        assertEquals(3, events.size)
        assertEquals(
            DanmakuEvent(
                id = "row-1",
                timestampMs = 1_250,
                text = "hello & world",
                style = DanmakuStyle(
                    colorArgb = 0xFFFFFFFFu,
                    mode = DanmakuMode.SCROLLING,
                    size = DanmakuSize.NORMAL,
                ),
            ),
            events[0],
        )
        assertEquals(DanmakuMode.TOP, events[1].style.mode)
        assertEquals(DanmakuSize.SMALL, events[1].style.size)
        assertEquals(0xFFFF0000u, events[1].style.colorArgb)
        assertEquals(DanmakuMode.BOTTOM, events[2].style.mode)
        assertEquals(DanmakuSize.LARGE, events[2].style.size)
        assertEquals(0xFF0000FFu, events[2].style.colorArgb)
    }

    @Test
    fun skipsInvalidXmlRows() {
        val events = LocalDanmakuParser.parseBilibiliXml(
            """
            <i>
              <d p="-1,1,25,16777215">negative</d>
              <d p="1,1,25,16777215">   </d>
              <d p="2,1,25,16777215">valid</d>
            </i>
            """.trimIndent(),
        )

        assertEquals(listOf("valid"), events.map(DanmakuEvent::text))
        assertEquals(2_000, events.single().timestampMs)
    }

    @Test
    fun parsesNormalizedJsonEnvelope() {
        val events = LocalDanmakuParser.parseNormalizedJson(
            """
            {
              "events": [
                {
                  "id": "one",
                  "timestampMs": 1200,
                  "text": "scroll",
                  "mode": "scrolling",
                  "size": "normal",
                  "color": "#00ff00"
                },
                {
                  "time": 2.5,
                  "text": "bottom",
                  "style": {
                    "mode": "bottom",
                    "size": "large",
                    "colorArgb": "4294901760"
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, events.size)
        assertEquals("one", events[0].id)
        assertEquals(1_200, events[0].timestampMs)
        assertEquals(0xFF00FF00u, events[0].style.colorArgb)
        assertEquals("json-1", events[1].id)
        assertEquals(2_500, events[1].timestampMs)
        assertEquals(DanmakuMode.BOTTOM, events[1].style.mode)
        assertEquals(DanmakuSize.LARGE, events[1].style.size)
        assertEquals(0xFFFF0000u, events[1].style.colorArgb)
    }

    @Test
    fun parsesNormalizedJsonArraysAndSkipsInvalidRows() {
        val events = LocalDanmakuParser.parseNormalizedJson(
            """
            [
              {"timestampMs": 100, "text": "valid"},
              {"timestampMs": -1, "text": "bad"},
              {"timestampMs": 200, "text": " "}
            ]
            """.trimIndent(),
        )

        assertEquals(1, events.size)
        assertEquals("json-0", events.single().id)
        assertEquals("valid", events.single().text)
    }
}
