---
name: miuix-ui
description: Build, review, and refactor Compose Miuix UI in IslandLyrics using Miuix 0.9.3 conventions, the app blur wrapper stack, theme tokens, popup-host rules, and compile-first verification. Use for any Miuix screen or component work; do not use for Material-only UI.
---

# Miuix UI

## First Decisions

1. Treat Material and Miuix as separate stacks. Change only the requested stack; never edit `ui/material` to fix a Miuix issue unless explicitly asked.
2. Prefer an existing upstream 0.9.3 component before creating a wrapper. Add a project wrapper only when it supplies real behavior such as blur, fallback, preference listening, predictive back, drag reorder, or cross-page style policy.
3. Read the closest reference before inventing layout:
   - Upstream source: `reference/miuix-0.9.3`
   - Chinese docs: `reference/miuix-0.9.3/docs/zh_CN/components` and `/guide`
   - Project wrappers: `app/src/main/java/com/example/islandlyrics/ui/miuix`

## Non-Negotiable Rules

- Wrap every page in a Miuix scaffold family component. `OverlayDialog`, `OverlayBottomSheet`, `OverlayListPopup`, dropdown preferences, and spinner preferences require its popup host.
- Keep popups on the same host as their trigger surface. Inside `MiuixBlurDialog`, set `renderInRootScaffold = false`; ordinary page controls normally keep the default `true`.
- Use `MiuixTheme.colorScheme.*` and `MiuixTheme.textStyles.*`. Do not hardcode colors, font sizes, or copy Material typography/colors.
- Do not replace upstream `BasicComponent`'s custom layout with `Row + weight(1f)`; its start/center/end distribution intentionally handles overflow.
- Respect minSdk/runtime capability for effects. Gate blur paths rather than assuming support; avoid recursive backdrop capture (never blur a surface from inside itself).
- Preserve unrelated files and user-visible labels exactly when making targeted changes.

## Project Defaults

- Theme: use `MiuixAppTheme` / existing controller; derive light/dark and custom seed behavior there.
- Page chrome: prefer `MiuixBlurScaffold` plus `MiuixBlurTopAppBar` / `MiuixBlurSmallTopAppBar`.
- Dialogs/sheets: prefer `MiuixBlurDialog` / `MiuixBlurBottomSheet` when the product expects blur; use plain Window APIs only when blur is not wanted.
- Dropdown settings rows: use `BlurOverlayDropdownPreference` (often imported as `SuperDropdown`) for the established blur + edge-highlight behavior.
- Lists: use `BasicComponent` / preference components first; promote a local row helper only after reuse appears across pages.
- Reordering: use `MiuixBlurReorderablePanel`; do not put it inside another vertically scrollable container without checking measurement.

## Component Selection

Use this order:

1. Direct upstream API: `Button`, `TextButton`, `Card`, `SwitchPreference`, `ArrowPreference`, `SliderPreference`, `TextField`, `TopAppBar`, etc.
2. Established app wrapper when blur/project behavior is required: `MiuixBlur*`, `BlurOverlayDropdownPreference`, `MiuixBackHandler`, `MiuixBackIcon`.
3. New shared component only with a real reusable behavior and an upstream-style API.
4. Page-local helper only if no other page needs it.

## Implementation Conventions

Follow upstream API shape:

```kotlin
@Composable
fun ComponentName(
    requiredStateOrCallback: ...,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ComponentColors = ComponentDefaults.componentColors(),
    content: @Composable () -> Unit,
)
```

- Put dimensions/constants in `ComponentDefaults`; expose colors through a `@Composable` defaults factory backed by theme tokens.
- Mark truly immutable color/style data classes `@Immutable`; use `@Stable` when they contain callbacks/state-backed properties.
- Avoid unstable raw collections in public composable state where practical.
- Use `rememberUpdatedState` only to keep long-lived closures current; forward callbacks directly when possible.
- Keep interaction state consistent: mixed-height right-side actions need explicit vertical alignment; whole-row click semantics must not conflict with the embedded control's own click target.
- For new screens, follow an existing feature screen under `feature/*/miuix`; do not introduce a second navigation/top-bar pattern.

## Blur And Effects

- Use one effect stack per surface. Do not mix Haze and miuix-blur approaches.
- Capture backdrop at the stable ancestor/container, then apply texture/blur/highlight to the foreground panel.
- Edge highlight is a separate opt-in behavior controlled by project preferences; do not bake a permanent global highlight into generic components.
- Verify dark mode, light mode, edge-to-edge insets, dialog layering, and fallback behavior—not just normal-page rendering.

## Verification

Run targeted compilation before claiming success:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain -q
```

Then choose the smallest additional check that proves the change:

- Existing unit/UI tests when applicable.
- Emulator/UIAutomator or screenshots for layout, gesture, popup layering, dark mode, and blur regressions.
- Explicitly distinguish code inference from visual/device verification; never claim you saw a screenshot unless one was actually inspected.

## References

Read only what is relevant:

- `references/components.md`: common components, selection rules, host requirements.
- `references/theme-and-effects.md`: theme tokens, text styles, blur/backdrop/highlight constraints.
- `references/code-review.md`: checklist for reviewing/refactoring Miuix changes.
