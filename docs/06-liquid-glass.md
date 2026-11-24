# Liquid glass design system

The whole iOS port uses one shared Apple Music-style glass language. It lives
in `composeApp/src/commonMain/kotlin/com/example/musicfy/ui/component/GlassKit.kt`.

## Why this exists

Upstream Android `ui/component/GlassKit.kt` records an Android `RenderNode`
backdrop and samples it with `RenderEffect`/`BlurEffect`. None of that exists
outside Android. The port keeps the **same architectural role** - one shared
backdrop used by the nav pill, mini player and every screen - but implements it
with Compose Multiplatform primitives.

## Rules for every ported screen

1. The app root is `GlassRoot`. Never replace it with an opaque `Surface`.
2. Use `GlassCard` instead of Material `Card` for content groups.
3. Use `glassSurface(...)` for floating chrome: nav pill, mini player, sheets,
   dialogs, menus, top bars.
4. Use `GlassNavigationBar` for tab switching. Do not reintroduce Material
   `NavigationBar` with an opaque `containerColor`.
5. Decorative colour comes from the shared aurora backdrop in `GlassRoot`, not
   from per-screen full-screen gradients.
6. A detail screen may set `LocalGlassState.current.accent` to tint the chrome,
   but must restore it when disposed.

## Tokens

- `GlassTokens.Chrome`: translucent near-black `0x99131317`
- `GlassTokens.Stroke`: white hairline `0x33FFFFFF`
- `GlassTokens.Highlight`: top-edge white sheen `0x14FFFFFF`
- `GlassTokens.Pink` / `Purple` / `Blue`: out-of-focus aurora washes
- `GlassTokens.Corner`: 28.dp; `GlassTokens.NavCorner`: 26.dp

## Platform notes

`Modifier.blur` is supported by Compose Multiplatform through Skia on iOS, so
the aurora is genuinely blurred. Where a future platform needs a fallback, the
layered translucent gradient + hairline border still reads as glass.
