# ui-kit: notes (work in progress)

Branch `port/ui-kit`. The shared UI kit is in; the overlay screens follow. This file is completed
at the end (file map, tests, deviations, visual checks).

## Using the kit (Kotlin → Godot)

Everything is in dp (one unit is one dp, see `Display`). Compose's layout is reproduced by the kit's
own nodes rather than Godot's containers, so sizes follow Compose's rules (wrap content, fixed size,
fill, weights, padding, shrink-to-fit) and snap to whole pixels.

| Kotlin | Godot |
|---|---|
| `Box(modifier, contentAlignment)` | `UiView.new()` (`content_align`, children placed by `align`) |
| `Column(horizontalAlignment, verticalArrangement)` / `Row(...)` | `UiColumn` (`h_align`, `arrange`, `spacing`) / `UiRow` (`v_align`, `arrange`, `spacing`) |
| `Spacer(Modifier.height(x))` / `.width(x)` | `UiSpacer.h(x)` / `UiSpacer.w(x)` |
| `Modifier.size/width/height/fillMaxWidth/fillMaxSize/heightIn(min)/aspectRatio` | `with_size`, `with_width`, `with_height`, `fill_max_width()`, `fill_max_size()`, `with_min_height`, `with_aspect` (or the fields `width_dp`, `fill_width`...) |
| `Modifier.padding` before / after the background | `margin` / `padding` (`with_margin*`, `with_padding*`) |
| `Modifier.weight(w, fill)` | `with_weight(w, fill)` |
| `Modifier.align(...)` in a Box | `aligned(x_bias, y_bias)` (-1 start, 0 centre, 1 end) |
| `background` / `border` / `drawBehind` | `bg = func(ds: DrawScope, size: Vector2)`; `Widgets.border(...)` draws a Compose border (inside the bounds) |
| `Modifier.uiGlow(color, corner, reach, strength)` | `with_glow(color, corner, reach, strength)` (or `glow_fn` for a pulsing one) |
| `graphicsLayer { alpha, scale, translation }` | `set_layer(alpha, scale, offset)` (moves what is drawn, not the layout) |
| `Modifier.clickable(onClick)` | `clickable = true`, `on_click`, `enabled` (press spring: `press_value()`, `press_shrink`) |
| `semantics { contentDescription }`, `clearAndSetSemantics` | `accessibility_name`, `access_clear = true`; roles `access_role`, `access_checked`, `access_selected`, `access_progress` |
| `Modifier.verticalScroll` / `LazyVerticalGrid(GridCells.Fixed(n))` | `UiScroll` (one child) / `UiScroll` holding a `UiGrid.fixed(n, spacing)` (`grid_span` for a full line) |
| `Modifier.shrinkToFit()` | `UiShrink.of(view)` (ArcadeText has `fit`) |
| `ArcadeText(text, modifier, color, unit, shadow, tiny, centered, alpha, maxWidth, tracking, fit)` | `ArcadeText.plain(text, color, unit, shadow, tiny, centered, alpha, max_width, tracking, fit)` |
| `ArcadeText(text, style, modifier, color, centered, alpha, shadow, fit)` | `ArcadeText.styled(text, style, color, centered, alpha, shadow, fit)` |
| `ArcadeButton(text, onClick, modifier, color, textColor, enabled, unit, tiny)` | `ArcadeButton.make(text, on_click, color, text_color, enabled, unit, tiny)` |
| `RoundButton(icon, onClick, color, modifier, size, label)` | `RoundButton.make(icon, on_click, color, size, label)` |
| `ArcadePanel(title, accent, onClose, modifier, fillHeight) { content }` | `ArcadePanel.make(title, accent, on_close, fill_height)` then `add_content(view)` |
| `GlassBox(modifier, highlight) { content }` | `GlassBox.make(highlight)` (a UiColumn; add children) |
| `CurrencyRow(tokens, tickets, modifier, unit, onTick)` | `CurrencyRow.make(tokens, tickets, unit, on_tick)`, `set_counts(t, k)`, `token_icon`, `ticket_icon` |
| `ArcadeBanner(text)` | `ArcadeBanner.new()`, `set_text(text)` ("" hides it) |
| `RollingNumber(value, color, unit, modifier, onTick)` | `RollingNumber.make(value, color, unit, on_tick)`, `set_value(v)` |
| `CurrencyFx` / `CurrencyFxLayer(fx)` | `CurrencyFx.new()` (positions in screen pixels, as build-13; `CurrencyFx.anchor_of(control)`), `CurrencyFxLayer.make(fx)` |
| `TokenIcon(size, twinkle)` / `TicketIcon(size)` | `TokenIcon.make(size, twinkle)` / `TicketIcon.make(size)` |
| `ArcadeDivider`, `SectionHeader`, `ArcadeChip`, `ArcadeProgressBar`, `CountdownRing`, `ArcadeToggle` | same names, `.make(...)` with Kotlin's parameters in order |
| `Modifier.cardFrame(edge, thick, glow, tint)` | `UiParts.card_frame(view, edge, thick, glow, tint)` |
| `DrawScope.glowRoundRect / glowCircle` | `UiTheme.glow_round_rect(ds, ...)` / `UiTheme.glow_circle(ds, ...)` |
| `drawUiIcon / drawToken / drawTicket / drawSparkle`, `UiIcon.X` | `UiIcons.draw_ui_icon(ds, ...)`, `draw_token`, `draw_ticket`, `draw_sparkle`; `UiIcon.X` (int) |
| `Color.lift(f)` / `Color.shade(f)` / `spokenText` | `Widgets.lift(c, f)` / `Widgets.shade(c, f)` / `Widgets.spoken_text(text)` |
| `stageOf`, `rememberEntrance`, `enterStage`, `staggerIn`, `popIn`, `reserveSpace` | `Widgets.stage_of`, `Widgets.remember_entrance(owner, millis)`, `Widgets.enter_stage(view, entrance, from, to, rise, pop)`, `Widgets.stagger_in(view, index)`, `Widgets.pop_in(view, delay_ms, key)`, `Widgets.reserve_space(view, show)` |
| `UiMotion.enabled` | `UiMotion.enabled` |
| `UiColors.cardTop` ... | `UiColors.card_top` ... (Godot Colors); `UiSpace.md`, `UiRadius.box`, `UiEdge.hair`, `UiGlow.IDLE`, `UiText.HEADING` |
| `WindowInsets.safeDrawing` | `Widgets.safe_insets(node)` (`Widgets.forced_insets` overrides it for captures) |

Animations step with frames on the node that owns them (`ui_step(dt)` drives them in tests); none
reads the wall clock. Compose's easings, springs (closed form) and infinite repeats are in `UiAnim`.
