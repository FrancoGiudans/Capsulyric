# Theme, Text, Blur, And Effects

## Theme

- Root Miuix composition uses `MiuixAppTheme`; it bridges app preferences to upstream `ThemeController`, dynamic/custom colors, and light/dark schemes.
- Access values only through:
  - `MiuixTheme.colorScheme.<token>`
  - `MiuixTheme.textStyles.<style>`
- Do not hardcode `Color.White`, raw grays, `MaterialTheme.colorScheme`, or ad-hoc font sizes.
- When a component needs a derived alpha/overlay, derive it from a semantic token and remember the derivation with meaningful inputs.
- New semantic needs belong in the project theme layer only if reused; otherwise compose existing tokens locally.

## Text

- Titles/body/summary/button text use matching `MiuixTheme.textStyles` values.
- Summary/secondary text generally uses the corresponding variant-summary/on-surface token, not an arbitrary translucent black/white.
- Preserve bilingual resource strings; add both default and `values-zh` resources when introducing user-facing text.

## Blur Stack

Project blur surfaces live in `app/src/main/java/com/example/islandlyrics/ui/miuix/blur`.

Preferred mapping:

| Surface | Component |
| --- | --- |
| Screen scaffold | `MiuixBlurScaffold` |
| Top bar | `MiuixBlurTopAppBar` / `MiuixBlurSmallTopAppBar` |
| Dialog | `MiuixBlurDialog` |
| Bottom sheet | `MiuixBlurBottomSheet` |
| Navigation bar | `MiuixBlurNavigationBar` |
| Snackbar | `MiuixBlurSnackbar` |
| Dropdown row | `BlurOverlayDropdownPreference` |

Rules:

- Capture backdrop at the intended background container; blur only panels intended to sample it.
- Never create recursive capture: a blurred child must not be part of the same backdrop being sampled by itself.
- Keep fallback behavior intact when runtime shader support is unavailable.
- Do not introduce Haze into Miuix surfaces; use the existing miuix-blur-based stack.
- Edge highlight follows project lab/preference gating; do not force-enable globally.

## Interaction Effects

- Overscroll/haptics already integrate through Miuix theme/factory where available.
- Use `Modifier.scrollEndHaptic()` only where boundary feedback is desired.
- Pressable sink/tilt feedback should match nearby controls; do not add competing indication systems.
- Mixed-height end actions need explicit center alignment so switches/icons/buttons line up.

## Visual Verification

Compile is mandatory; device checks are required for visual claims:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain -q
```

For UI-sensitive changes, inspect actual screenshots/UIAutomator output on emulator or device. Report separately:
1. Code inference
2. Compile evidence
3. Device/visual evidence
