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
