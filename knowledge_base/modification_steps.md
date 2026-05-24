# Modification Steps

1. Added expression parsing for x/y values (`w` and `h`) in `UiValue`.
2. Extended text elements with `font` and updated rendering to honor font ids.
3. Updated UI schema and sample YAML to include `z` and `font`.
4. Documented custom font setup with TTF + font JSON.
5. Rebuilt to confirm no compile errors.
6. Simplified YAML loading to use the default SnakeYAML constructor.
7. Added scroll list component with nested children and scroll direction settings.
8. Added click actions with JS execution and overlay rendering for inventory/chest.
9. Added slot elements and GIF image support.
10. Added Rhino JS dependency for script execution.
11. Added mixin-based replacement render for inventory/chest overlays.
12. Added chest title matching (color codes ignored) and replace_vanilla config flag.
13. Made slot element rendering scale to configured width/height.
14. Added inventory render cancellation mixin and slot hover mask configuration.
15. Added ScreenEvent.Render.Pre cancellation when replace_vanilla is enabled.
16. Added UI open/close events with JS playback hooks and an audio controller.
17. Moved audio playback to a standalone audio module and switched to direct `assets/magicmod/music/*.ogg` paths.
18. Added hover actions, ESC override/movement options, and a JS setter for element properties.
19. Added `esc.yml` and a pause-screen hook that only replaces ESC when the player is in-world.
20. Added HUD overlay support, JS stat helpers, and a sample `hud.yml`.
21. Ensured movement keybindings stay active during `allow_move` screens by widening key conflict context.
22. Added per-element opacity for `text`/`image`, UI animation definitions, and JS animation playback helpers.
23. Updated `loading.yml` and UI schema docs with animation examples.
24. Added GPU shader rendering system with VBO batching for improved performance.
25. Created vertex shader (ui_shader.vsh) implementing MVP transformations and matrix operations.
26. Created fragment shader (ui_shader.fsh) for texture sampling and color blending.
27. Implemented UiShaderRenderer class for VBO management and shader invocation.
28. Added `shader_render` boolean flag to UiConfig for toggling CPU/GPU rendering.
29. Enhanced anchor system with separate `anchor_x` and `anchor_y` controls for flexible positioning.
30. Created UiAnchorAxis class supporting independent horizontal (left/center/right) and vertical (top/center/bottom) anchors.
31. Updated UiScreen to use anchorAxis for all element positioning.
32. Created shader_demo.yml demonstrating shader rendering and enhanced anchor capabilities.
33. Added `entity` element type for rendering LivingEntity targets, with mouse-facing support.
34. Added entity target fields (`target_type`, `player_name`, `entity_uuid`, `look_at_mouse`, `entity_scale`) and runtime setters.
35. Added player entity rendering to `hud.yml`.
36. Added `run_client_java.bat` to set `JAVA_HOME` and launch the client.
