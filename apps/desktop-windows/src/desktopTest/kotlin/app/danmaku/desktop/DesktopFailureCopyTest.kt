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

    @Test
    fun localPlaybackPreparationUsesLocalizedCopyForExpectedFailures() {
        val english = DesktopUiLanguage.ENGLISH.strings
        val traditionalChinese = DesktopUiLanguage.ZH_TW.strings

        assertEquals(
            "This episode is missing its indexed file mapping. Rescan the library and try again.",
            DesktopUserActionException(
                message = "Indexed media file is missing for episode-1",
                kind = DesktopUserActionFailureKind.INDEXED_MEDIA_MAPPING_MISSING,
            ).localPlaybackPrepareErrorMessage(english),
        )
        assertEquals(
            "The indexed media file is no longer available. Check the folder or rescan the library.",
            DesktopUserActionException(
                message = "Indexed media file no longer exists: S:/missing.mkv",
                kind = DesktopUserActionFailureKind.INDEXED_MEDIA_FILE_MISSING,
            ).localPlaybackPrepareErrorMessage(english),
        )
        assertEquals(
            "此集缺少索引檔案對應。請重新掃描媒體庫後再試一次。",
            DesktopUserActionException(
                message = "Indexed media file is missing for episode-1",
                kind = DesktopUserActionFailureKind.INDEXED_MEDIA_MAPPING_MISSING,
            ).localPlaybackPrepareErrorMessage(traditionalChinese),
        )
        assertEquals(
            "索引中的媒體檔案已無法使用。請檢查資料夾或重新掃描媒體庫。",
            DesktopUserActionException(
                message = "Indexed media file no longer exists: S:/missing.mkv",
                kind = DesktopUserActionFailureKind.INDEXED_MEDIA_FILE_MISSING,
            ).localPlaybackPrepareErrorMessage(traditionalChinese),
        )
    }
}
