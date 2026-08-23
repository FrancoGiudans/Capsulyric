# Miuix Components Quick Reference

Source of truth: `C:\Android\IslandLyrics\reference\miuix-0.9.3`.

## Scaffold And Hosts

- `top.yukonga.miuix.kmp.basic.Scaffold` owns popup/dialog state via `MiuixPopupHost`.
- Required hosts:
  - `OverlayDialog`
  - `OverlayBottomSheet`
  - `OverlayListPopup`
  - `OverlayDropdownPreference`
  - `OverlaySpinnerPreference`
- `renderInRootScaffold = true` renders against the root scaffold/full screen. Set it to `false` when a nested dropdown belongs to a dialog-local scaffold/popup host.
- Plain `WindowDialog` / `WindowBottomSheet` are platform-window variants and do not need a Miuix scaffold host, but they bypass the app's overlay/blur composition model.

## Common Controls

| Need | Preferred API |
| --- | --- |
| Standard list row | `BasicComponent` |
| Clickable setting row | `ArrowPreference` |
| Toggle row | `SwitchPreference` |
| Numeric/slider setting | `SliderPreference` |
| Select-from-list row | `OverlayDropdownPreference` / app `BlurOverlayDropdownPreference` |
| Primary/action button | `Button` / `TextButton` |
| Grouped surface | `Card` |
| Input | `TextField` / `SearchBar` |
| Page title | `TopAppBar` / `SmallTopAppBar` |
| Modal blur surface | app `MiuixBlurDialog` |
| Persistent blur sheet | app `MiuixBlurBottomSheet` |

## BasicComponent

- Use title + summary overload for normal rows.
- Use `startAction` for icons/avatars and `endActions` for switches/arrows/menus.
- Use custom-content overload only when title/summary cannot express the row.
- Keep long summaries bounded with ellipsis where appropriate; verify narrow-width overflow.
- Never reimplement its internal weighted custom layout as a simple weighted row.

## Preference Rows

- State should live above the row; pass value/read-write callback consistently with neighboring code.
- `enabled = false` must disable both appearance and action semantics.
- Dropdown entries need stable keys/values; selected label should come from the same source used by persistence.
- If a dropdown is visually inside a blurred dialog, align its popup host with that dialog using `renderInRootScaffold = false`.

## Cards And Buttons

- Use upstream corner radius, padding, and color factories before adding custom constants.
- Interactive cards/buttons should preserve disabled and pressed states.
- Avoid nesting clickable cards inside clickable rows unless the semantic boundary is intentional and testable.

## Sheets And Dialogs

- Confirm/cancel dialogs: keep destructive action distinct and accessible.
- Forms in sheets/dialogs must account for IME/insets.
- Long content needs explicit scrolling policy; avoid unbounded vertical content in bottom sheets.
- Popup/dialog close behavior must handle system back, outside tap, and programmatic dismissal coherently.
