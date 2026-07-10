package app.danmaku.desktop

import app.danmaku.domain.ExternalAnimeListStatus
import app.danmaku.domain.ExternalAnimeTrackingPlanConflictReason
import app.danmaku.domain.ExternalAnimeTrackingPlanSkipReason
import app.danmaku.domain.LocalAnimeListStatus
import app.danmaku.domain.ExternalAnimeTrackingPlanSummary
import app.danmaku.domain.PlaybackStatus

internal fun PlaybackStatus.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        PlaybackStatus.IDLE -> strings.playerIdleStatusLabel
        PlaybackStatus.LOADING -> strings.loadingLabel
        PlaybackStatus.READY -> strings.readyStatusLabel
        PlaybackStatus.PLAYING -> strings.playingLabel
        PlaybackStatus.PAUSED -> strings.playerPausedStatusLabel
        PlaybackStatus.ENDED -> strings.playerEndedStatusLabel
        PlaybackStatus.ERROR -> strings.playerErrorStatusLabel
    }

internal fun ExternalAnimeProviderSettings.myAnimeListStatusLabel(strings: DesktopStrings): String =
    when {
        myAnimeListClientId == null -> strings.providerNotConfiguredStatus
        hasMyAnimeListAccessToken -> strings.myAnimeListOauthTokenSavedStatus
        hasMyAnimeListClientSecret -> strings.myAnimeListClientIdSecretSavedStatus
        else -> strings.myAnimeListClientIdSearchSavedStatus
    }

internal fun ExternalAnimeProviderSettings.bangumiStatusLabel(strings: DesktopStrings): String =
    if (hasBangumiAccessToken) {
        strings.bangumiAccessTokenSavedStatus
    } else {
        strings.bangumiPublicSearchOnlyStatus
    }

internal fun DandanplayProviderSettings.statusLabel(strings: DesktopStrings): String =
    when {
        hasCredentials -> strings.dandanplayConfiguredStatus(
            appId?.redactStatusToken() ?: "",
            authenticationMode.localizedLabel(strings),
        )
        isFetchEnabled -> strings.dandanplayCompatibleApiStatus
        else -> strings.dandanplayNotConfiguredStatus
    }

internal fun ExternalAnimeTrackingPlanSummary.localizedLabel(strings: DesktopStrings): String =
    strings.externalSyncSummaryLabel(updateCount, conflictCount, skippedCount)

internal fun ExternalAnimeTrackingPlanSkipReason.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        ExternalAnimeTrackingPlanSkipReason.MISSING_LOCAL_SERIES -> strings.missingLocalSeriesReason
        ExternalAnimeTrackingPlanSkipReason.UNMAPPED_LOCAL_SERIES -> strings.unmappedLocalSeriesReason
    }

internal fun ExternalAnimeTrackingPlanConflictReason.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        ExternalAnimeTrackingPlanConflictReason.EXTERNAL_PROGRESS_AHEAD -> strings.externalProgressAheadReason
    }

internal fun ExternalAnimeListStatus?.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        ExternalAnimeListStatus.WATCHING -> strings.externalListWatchingStatus
        ExternalAnimeListStatus.COMPLETED -> strings.externalListCompletedStatus
        ExternalAnimeListStatus.ON_HOLD -> strings.externalListOnHoldStatus
        ExternalAnimeListStatus.DROPPED -> strings.externalListDroppedStatus
        ExternalAnimeListStatus.PLAN_TO_WATCH -> strings.externalListPlanToWatchStatus
        null -> strings.externalListUnchangedStatus
    }

internal fun LocalAnimeListStatus.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        LocalAnimeListStatus.WATCHING -> strings.externalListWatchingStatus
        LocalAnimeListStatus.COMPLETED -> strings.externalListCompletedStatus
        LocalAnimeListStatus.ON_HOLD -> strings.externalListOnHoldStatus
        LocalAnimeListStatus.DROPPED -> strings.externalListDroppedStatus
        LocalAnimeListStatus.PLAN_TO_WATCH -> strings.externalListPlanToWatchStatus
    }

internal fun DandanplayAuthenticationMode.localizedLabel(strings: DesktopStrings): String =
    when (this) {
        DandanplayAuthenticationMode.SIGNED -> strings.signedAuthAction
        DandanplayAuthenticationMode.CREDENTIAL -> strings.credentialAuthAction
    }

private fun String.redactStatusToken(): String =
    when {
        length <= 4 -> "<redacted>"
        length <= 8 -> "${take(2)}...${takeLast(2)}"
        else -> "${take(4)}...${takeLast(4)}"
    }
