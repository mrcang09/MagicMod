# UI Steps

1. Create or edit a YAML file under `src/main/resources/assets/magicmod/ui/`.
2. (Optional) Add textures under `src/main/resources/assets/magicmod/textures/` and reference them as `magicmod:textures/...`.
3. (Optional) Add custom font via resource definitions:
   - Place TTF at `src/main/resources/assets/magicmod/font/arcana.ttf`
   - Create `src/main/resources/assets/magicmod/font/arcana.json`:

```
{
  "providers": [
    { "type": "ttf", "file": "magicmod:font/arcana.ttf", "size": 18 }
  ]
}
```

   - Use `font: magicmod:arcana` in the YAML text element.
4. Launch the client with `gradlew runClient`.
5. In-game, run `/open loading.yml` to open the UI.
6. If you edit the YAML while the game is running, reopen with `/open loading.yml`.
   - If the resource manager does not pick up changes, use `F3+T` to reload resources.

Notes:
- The loader reads from disk first (dev mode), then falls back to the resource manager.
- `/open` accepts file names with or without the `.yml` extension.
- Actions are JavaScript snippets. Example:

```
actions:
  left_click: "ui.toggle('log_list')"
  right_click: "ui.open('loading.yml')"
```

- Overlay UIs are loaded automatically when these files exist:
  - `inventory.yml` for the player inventory screen.
  - `chest.yml` for chest screens.
- Use `replace_vanilla: true` if you want to hide the vanilla container rendering.
- For chest overlays, set `match_titles` to the container title(s) you want to target.
  - Color codes are ignored when matching; `&` and `§` are accepted in config strings.
- For slot hover highlights, set `hover_mask: true` on `slot` elements.
- UI events are defined at top level and accept JS:

```
events:
  open: "ui.playMusic('ui_test.ogg', 0.6, true)"
  close: "ui.stopMusic('ui_test.ogg')"
```

- Audio loads OGG files directly from `assets/<namespace>/music/`.
  - Example file `assets/magicmod/music/ui_test.ogg` can be played with `ui.playMusic('ui_test.ogg', 0.6, true)` or `ui.playMusic('magicmod:music/ui_test.ogg', 0.6, true)`.
  - `ui.playMusic(path, volume, loop, stopAtMs)` uses `stopAtMs` only when `loop` is `false`.
