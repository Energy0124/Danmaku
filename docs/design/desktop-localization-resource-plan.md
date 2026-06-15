# Desktop Localization Resource Plan

Last updated: 2026-06-15.

## Goal

Move desktop UI text out of the giant Kotlin `DesktopStrings` initializer and
into Compose Multiplatform string resources without breaking the existing
in-app language selector.

## Current Slice

- All current desktop `DesktopStrings` fields have generated resource keys in
  `apps/desktop-windows/src/commonMain/composeResources/values/strings.xml`.
- Traditional Chinese resources live in
  `apps/desktop-windows/src/commonMain/composeResources/values-zh-rTW/strings.xml`.
- `DesktopStringResources.kt` adapts generated `Res.string.*` values back into
  the existing `DesktopStrings` model for composable UI.
- `DesktopStringResources.kt` sets the desktop Java default locale from the
  selected app language before reading generated resources. This uses the
  current public desktop behavior, where Compose resource lookup follows
  `java.util.Locale.getDefault()`.
- `DesktopShellNavigationState` remains non-composable and does not call
  `stringResource` directly.

## Constraints

- Compose resources in the current dependency version use the current Compose
  locale for `stringResource`; the public API does not expose a direct
  composable resource-environment override.
- The desktop app-language selector owns the Java default locale for generated
  resource lookup. If Compose exposes a public resource-environment override in
  a later dependency version, prefer that narrower integration.
- Placeholder and lambda-backed strings should move as explicit XML format
  strings, then be exposed through small composable adapters rather than
  calling `stringResource` from state or action classes.

## Next Steps

1. Run English and `zh-TW` screenshot QA across dense desktop surfaces.
2. Remove duplicated migrated fallback text from the Kotlin initializer after
   screenshot checks pass.
3. Consider moving the desktop locale-owner helper to a smaller dedicated owner
   module if future platforms need different resource-locale behavior.
