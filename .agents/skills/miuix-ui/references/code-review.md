# Miuix UI Review Checklist

## Scope

- Confirm the change touches only the Miuix stack unless explicitly requested otherwise.
- Preserve unrelated dirty worktree files and exact user-facing labels.

## Architecture

- Is each new wrapper justified by real behavior, not just styling?
- Could an upstream 0.9.3 component or existing project wrapper do the same job?
- Are page-local helpers kept local until reused?
- Is state owned above presentation and passed through a minimal interface?

## Composition And Hosts

- Every overlay/dropdown has a valid Miuix scaffold host.
- Nested dialog + dropdown uses the same local host (`renderInRootScaffold = false`).
- Ordinary page-level dropdown does not accidentally lose full-screen/root positioning.
- Back/outside-tap/programmatic dismissal paths are coherent.

## Theming

- No hardcoded colors or typography.
- Light/dark variants both derive from semantic tokens.
- Disabled/disabled-content states use token pairs.
- User-facing strings exist in all required locales.

## Layout And Accessibility

- Long titles/summaries have overflow behavior.
- Start/end actions remain vertically aligned.
- Whole-row click does not conflict with embedded switch/control.
- Touch targets, content descriptions, roles, and click labels are preserved/improved.
- Insets, IME, landscape/narrow width, and predictive back are considered.

## Effects

- One blur/effect stack is used.
- Backdrop ownership is clear and non-recursive.
- Fallback path exists and compiles for unsupported devices.
- Edge highlight remains gated by existing preferences.

## Verification

Required minimum:

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain -q
```

Additional evidence by risk:

- Low-risk text/token tweak: compile + focused diff review.
- Layout/component change: emulator screenshot or UI tree.
- Popup/dialog/sheet/gesture/reorder: interactive device/emulator check.
- Effect/theme change: light + dark and fallback checks.

Do not claim visual verification without actually viewing a screenshot or UI dump.
