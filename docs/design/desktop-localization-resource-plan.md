# Desktop Localization Resource Plan

Last updated: 2026-06-12.

## Goal

Move desktop UI text out of the giant Kotlin `DesktopStrings` initializer and
into Compose Multiplatform string resources without breaking the existing
in-app language selector.

## Current Slice

- Shell navigation/header chrome and settings section/general-settings label
  resources live in
  `apps/desktop-windows/src/commonMain/composeResources/values/strings.xml`.
- Traditional Chinese resources live in
  `apps/desktop-windows/src/commonMain/composeResources/values-zh-rTW/strings.xml`.
- `DesktopStringResources.kt` adapts generated `Res.string.*` values back into
  the existing `DesktopStrings` model for composable UI.
- `DesktopShellNavigationState` remains non-composable and does not call
  `stringResource` directly.

## Constraints

- Compose resources in the current dependency version use the current Compose
  locale for `stringResource`; the public API does not expose a direct
  composable resource-environment override.
- Until the language selector is wired into Compose locale selection, the
  adapter must preserve selected-language fallback strings when the selected
  app language and current Compose locale differ.
- Placeholder and lambda-backed strings should move as explicit XML format
  strings, then be exposed through small composable adapters rather than
  calling `stringResource` from state or action classes.

## Next Steps

1. Move shell/playback/dialog strings with placeholders into XML format
   strings.
2. Move playback controls and danmaku panel labels into resources.
3. Introduce a locale-owner strategy for desktop language selection so
   generated resources can be the source of truth independent of system locale.
4. Remove migrated fields from the Kotlin initializer only after their resource
   adapter coverage and English/`zh-TW` screenshot checks are in place.
