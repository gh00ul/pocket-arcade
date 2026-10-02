# Photo booth, strips, saving and sharing (agent: photo)

Branch `port/photo`. Work in progress; this file is completed at the end.

## Files

| Kotlin | Godot | Status |
|---|---|---|
| `share/PhotoStrip.kt` (`PhotoStrip`) | `godot/scripts/share/photo_strip.gd` | done |
| `share/PhotoStrip.kt` (`StripLayout`, `PxRect`) | `godot/scripts/share/strip_layout.gd`, `px_rect.gd` | done (files of their own: the hall's photo wall test uses `StripLayout`) |
| `share/PhotoStore.kt` | `godot/scripts/share/photo_store.gd` | done |
| `share/PhotoStripArt.kt` | `godot/scripts/share/photo_strip_art.gd` | done |
| `ui/PhotoBoothPlan.kt` | `godot/scripts/ui/photo_booth_plan.gd` | done |
| `ui/PhotoBoothScreen.kt` (logic) | `godot/scripts/ui/photo_booth_session.gd` | done |
| `ui/PhotoBoothScreen.kt` (drawing, studio) | `godot/scripts/ui/photo_booth_screen.gd` | todo (waits for the UI kit and HallArt) |

## Open items for others

- TexPaint CLEAR (engine): `PaintJob` clears with a multiply layer that uses the paint's colour,
  but Android's CLEAR ignores the colour. A paint that was `reset()` (opaque black) then set to
  CLEAR multiplies RGB to 0 and keeps alpha, so the "hole" is opaque black. Seen in
  `Figure._paint_hair` (the face cut-out reads back as (0, 0, 0, 1)), which puts a black band over
  every kid's face. Fix: draw clear layers with Color(0, 0, 0, 0) whatever the paint's colour (in
  `PaintLayer._draw` for layers with the "clear" meta, or `TexPaint._push` setting the copy's
  colour to 0 when `xfer == CLEAR`).
