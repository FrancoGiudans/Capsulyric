# Miuix 0.9.4 Migration Notes

Use `reference/miuix` as the checked-out source of truth. Recompile application wrappers against 0.9.4 because public signatures changed from 0.9.3.

## Navigation

- The navigation runtime is `top.yukonga.miuix.kmp:miuix-nav`; `NavKey`, `NavDisplay`, `NavController`, and back-stack APIs are under `top.yukonga.miuix.kmp.nav`.
- `rememberNavBackStack<Routes>(Routes.Home)` requires a serializable route hierarchy and an explicit supertype when the stack contains multiple route variants. Use `navBackStackOf` for deliberately in-memory keys.
- Built-in transition presets do not enable page swipe-to-dismiss. Opt in with `entry(swipeDismiss = ...)` only when the product calls for an in-content drag gesture.
- `LocalNavTransitionScope.current` is available only within a `NavDisplay` entry. System predictive back and entry-local handlers share the display's scoped dispatcher; avoid registering a duplicate handler for the same stack.
- In this app, `PageStackHost(useMiuixNavForDefault = true)` selects miuix-nav only for Miuix PageSpecific mode while predictive back is enabled. The inline Miuix rule editor uses a Modal entry. Material and Consistent mode retain their existing hosts.

## Component API Changes

- `RadioButtonPreference` color parameters moved into `RadioButtonPreferenceColors`; use the color factory and named arguments where positional order changed.
- `NavigationRail` now has separate fixed-layout and state-based expandable overloads. Remove `state = null`; branch before calls when the state is nullable.
- `NavigationBarItem` and `FloatingNavigationBarItem` place `colors` before `badge`; use `badge = ...` for positional call sites. Prefer `NavigationBarDefaults.navigationBarItemColors`; selection opacity multiplies the supplied color alpha.
- `InputField` adds `color` before `leadingIcon`; `drawBackdrop` adds `progressiveGradient` before `enabled`. Prefer named arguments for affected calls.
- Pull-to-refresh exposes separate pull, full-drag, and visual progress. Use `refreshState == RefreshState.ThresholdReached` to render the armed state.
- Centered dialogs now default to a 32dp corner radius; bottom-attached dialogs use a screen-derived 32dp–48dp radius. Preserve explicit project overrides where needed.
- Published Miuix modules require minSdk 24; `miuix-blur` separately requires API 33. Verify the consuming app's minimum SDK and all call sites during an upgrade.
