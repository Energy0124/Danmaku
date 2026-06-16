package app.danmaku.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFailureCopyTest {
    @Test
    fun metadataMatchSearchUsesLocalizedCopyForExpectedFailures() {
        val english = DesktopUiLanguage.ENGLISH.strings
        val traditionalChinese = DesktopUiLanguage.ZH_TW.strings

        assertEquals(
            "No metadata search provider is configured. Add MyAnimeList or Bangumi settings first.",
            DesktopUserActionException("No external anime search providers are configured")
                .metadataMatchSearchErrorMessage(english),
        )
        assertEquals(
            "Provider search failed. Check provider settings and try again.",
            ExternalAnimeProviderException("Bangumi returned HTTP 500")
                .metadataMatchSearchErrorMessage(english),
        )
        assertEquals(
            "尚未設定中繼資料搜尋服務。請先新增 MyAnimeList 或 Bangumi 設定。",
            DesktopUserActionException("No external anime search providers are configured")
                .metadataMatchSearchErrorMessage(traditionalChinese),
        )
        assertEquals(
            "服務搜尋失敗。請檢查服務設定後再試一次。",
            ExternalAnimeProviderException("Bangumi returned HTTP 500")
                .metadataMatchSearchErrorMessage(traditionalChinese),
        )
    }
}
