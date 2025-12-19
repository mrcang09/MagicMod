# UI YAML Schema

Top level:

```
id: "magic_loading"
title: "Magic Loading"
shader_render: false
background:
  color: "#0b0f14"
  gradient_to: "#162433"
  texture: "magicmod:textures/ui/bg.png"
  texture_width: 256
  texture_height: 256
elements:
  - id: "title"
    type: "text"
    text: "MAGIC MOD"
    x: "50%"
    y: "18%"
    anchor: "center"
    anchor_x: "center"
    anchor_y: "top"
    color: "#f2c94c"
    scale: 2.4
    shadow: true
```

## Fields

Top level:
- `id`: string, optional.
- `title`: string, optional, used for the screen title.
- `allow_move`: boolean, optional; when true the player can keep moving while the UI is open.
- `override_esc`: boolean, optional; when true ESC won't close the UI (use `events.esc` to handle it).
- `hud`: boolean, optional; when true this UI is treated as a HUD overlay.
- `replace_hud`: boolean, optional; when true the HUD replaces the vanilla HUD layers.
- `draw_background`: boolean, optional; when false skips drawing the UI background (useful for HUD).
- `replace_vanilla`: boolean, optional; when true the UI replaces the vanilla container render.
- `shader_render`: boolean, optional; when true uses GPU shader rendering instead of CPU rendering (default: false).
- `match_titles`: list of strings, optional; when set, the overlay only applies if the screen title matches.
- `events`: map of event keys to JS scripts (`open`, `close`, `esc`).
- `open_animation`: string, optional; animation id to play when the UI opens.
- `animations`: list of animation entries, optional.
- `background`: map, optional.
- `elements`: list, optional.

Background:
- `color`: hex string or int (ARGB or RGB). Default `#0b0f14`.
- `gradient_to`: hex string or int. When set, draws a vertical gradient.
- `texture`: resource location string (e.g. `magicmod:textures/ui/bg.png`).
- `texture_width`, `texture_height`: int, default `256`.

Element common fields:
- `id`: string.
- `type`: `text`, `image`, `rect`, `progress`, `scroll` (alias `scroll_list`).
- `x`, `y`: number or percent string (e.g. `120`, `"50%"`).
- `width`, `height`: int (used by `image`, `rect`, `progress`).
- `anchor`: `top_left`, `top_center`, `top_right`, `center_left`, `center`, `center_right`, `bottom_left`, `bottom_center`, `bottom_right` (legacy, combined anchor).
- `anchor_x`: `left`, `center`, `right` (horizontal anchor, overrides `anchor` X-axis).
- `anchor_y`: `top`, `center`, `bottom` (vertical anchor, overrides `anchor` Y-axis).
- `z`: int for draw order (lower first).
- `x`, `y`: number, percent string (e.g. `120`, `"50%"`), or expression like `0.5*w` / `0.5*h` where `w` and `h` are screen width/height.
- `visible`: boolean (default `true`).
- `actions`: map of action keys to JS scripts.

Text:
- `text`: string.
- `color`: hex string or int.
- `opacity`: float 0..1 (optional).
- `scale`: float (default `1.0`).
- `shadow`: boolean.
- `align`: `left`, `center`, `right` (optional).
- `font`: `original` or a font id (e.g. `magicmod:arcana`).
- Text supports tokens like `{health}`, `{max_health}`, `{food}`, `{exp_level}`, `{exp}`, `{exp_total}`, `{target_type}`, `{target_name}`, `{target_health}`.

Image:
- `texture`: resource location string.
- `u`, `v`: int texture offsets (default `0`).
- `texture_width`, `texture_height`: int, default `256`.
- `opacity`: float 0..1 (optional).
- GIF: when `texture` ends with `.gif`, it will render animated frames.

Rect:
- `color`: hex string or int.

Progress:
- `fill_color`: hex string or int.
- `bg_color`: hex string or int (optional).
- `mode`: `static` or `time`.
- `value`: float, used for `static` (default `0`).
- `max`: float, used for `static` (default `1`).
- `duration_ms`: int, used for `time` (default `2000`).

Scroll:
- `color`: hex string or int (background fill, optional).
- `children`: list of element maps.
- `scroll_direction`: `vertical`, `horizontal`, or `both`.
- `scroll_step`: float (pixels per wheel step).
- `scroll_x`, `scroll_y`: float (initial offsets).

Slot:
- `slot`: int slot index from the current container menu.
- `width`, `height`: optional; defaults to 16 if not set.
- `hover_mask`: boolean, draw a translucent hover overlay when the mouse is over the slot.
- HUD slot indices: `0-8` hotbar, `40` offhand, `41` main hand.

Actions:
- `left_click`, `right_click`, `middle_click`
- `shift_left_click`, `shift_right_click`, `shift_middle_click`
- `enter`, `leave` (hover)

Animations:
- `id`: string, required.
- `target`: `screen` or `element`.
- `element` / `element_id`: element id (required when target is `element`).
- `duration_ms`: int, default `600`.
- `loop`: boolean, default `false`.
- `scale_from`, `scale_to`: float scale range (default `1`).
- `alpha_from`, `alpha_to`: float alpha range (default `1`).
- `translate_x_from`, `translate_x_to`: float pixel offsets (default `0`).
- `translate_y_from`, `translate_y_to`: float pixel offsets (default `0`).
- `rotate_from`, `rotate_to`: float degrees (default `0`).
- `pivot_x`, `pivot_y`: float 0..1 relative pivot (default `0.5`).

Notes:
- `match_titles` ignores color codes. Both `&` and `§` are accepted in the config.
- `events.open` fires when the UI is initialized; `events.close` fires when it is closed.
- Use `ui.set(id, property, value)` in scripts to change element properties at runtime.
- Use `ui.create(parentId, element)` to dynamically create elements in JS (`parentId` optional).
- Use `ui.playAnimation(id)` or `ui.playAnimation(id, elementId)` to play animations.
- Use `ui.stopAnimation(id)` to stop them.
