package org.test.magicmod.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class UiConfig {
    public final String id;
    public final String title;
    public final boolean allowMove;
    public final boolean overrideEsc;
    public final boolean hud;
    public final boolean replaceHud;
    public final boolean drawBackground;
    public final boolean replaceVanilla;
    public final boolean shaderRender;
    public final boolean useAtlas;
    public final int atlasSize;
    public final List<String> matchTitles;
    public final Map<String, String> events;
    public final String openAnimation;
    public final Map<String, UiAnimation> animations;
    public final UiBackground background;
    public final List<UiElement> elements;

    private UiConfig(String id, String title, boolean allowMove, boolean overrideEsc, boolean hud, boolean replaceHud,
                     boolean drawBackground, boolean replaceVanilla, boolean shaderRender, boolean useAtlas, int atlasSize,
                     List<String> matchTitles, Map<String, String> events, String openAnimation, Map<String, UiAnimation> animations,
                     UiBackground background, List<UiElement> elements) {
        this.id = id;
        this.title = title;
        this.allowMove = allowMove;
        this.overrideEsc = overrideEsc;
        this.hud = hud;
        this.replaceHud = replaceHud;
        this.drawBackground = drawBackground;
        this.replaceVanilla = replaceVanilla;
        this.shaderRender = shaderRender;
        this.useAtlas = useAtlas;
        this.atlasSize = atlasSize;
        this.matchTitles = matchTitles;
        this.events = events;
        this.openAnimation = openAnimation;
        this.animations = animations;
        this.background = background;
        this.elements = elements;
    }

    static UiConfig fromMap(Map<String, Object> root) {
        String id = getString(root, "id", "ui");
        String title = getString(root, "title", id);
        boolean allowMove = getBoolean(root, "allow_move", false);
        boolean overrideEsc = getBoolean(root, "override_esc", false);
        boolean hud = getBoolean(root, "hud", false);
        boolean drawBackground = getBoolean(root, "draw_background", !hud);
        boolean replaceHud = getBoolean(root, "replace_hud", hud);
        boolean replaceVanilla = getBoolean(root, "replace_vanilla", false);
        boolean shaderRender = getBoolean(root, "shader_render", false);
        boolean useAtlas = getBoolean(root, "use_atlas", false);
        int atlasSize = getInt(root, "atlas_size", 128);
        List<String> matchTitles = getStringList(root, "match_titles");
        Map<String, String> events = parseActions(getMap(root, "events"));
        String openAnimation = getString(root, "open_animation", null);
        Map<String, UiAnimation> animations = parseAnimations(getList(root, "animations"));
        UiBackground background = UiBackground.fromMap(getMap(root, "background"));
        List<UiElement> elements = parseElements(getList(root, "elements"));
        return new UiConfig(id, title, allowMove, overrideEsc, hud, replaceHud, drawBackground, replaceVanilla,
            shaderRender, useAtlas, atlasSize, matchTitles, events, openAnimation, animations, background, elements);
    }

    private static List<UiElement> parseElements(List<Object> rawElements) {
        List<UiElement> elements = new ArrayList<>();
        if (rawElements.isEmpty()) {
            return elements;
        }
        for (Object rawElement : rawElements) {
            if (rawElement instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> elementMap = (Map<String, Object>) map;
                elements.add(UiElement.fromMap(elementMap));
            }
        }
        elements.sort((left, right) -> Integer.compare(left.z, right.z));
        return elements;
    }

    private static Map<String, UiAnimation> parseAnimations(List<Object> rawAnimations) {
        if (rawAnimations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, UiAnimation> animations = new java.util.HashMap<>();
        for (Object rawAnimation : rawAnimations) {
            if (rawAnimation instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> animationMap = (Map<String, Object>) map;
                UiAnimation animation = UiAnimation.fromMap(animationMap);
                if (animation != null && animation.id != null && !animation.id.isBlank()) {
                    animations.put(animation.id, animation);
                }
            }
        }
        return animations;
    }

    static final class UiBackground {
        final int color;
        final Integer gradientTo;
        final String texture;
        final int textureWidth;
        final int textureHeight;

        private UiBackground(int color, Integer gradientTo, String texture, int textureWidth, int textureHeight) {
            this.color = color;
            this.gradientTo = gradientTo;
            this.texture = texture;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        static UiBackground fromMap(Map<String, Object> map) {
            int color = parseColor(map.get("color"), 0xFF0B0F14);
            Integer gradientTo = map.containsKey("gradient_to")
                ? parseColor(map.get("gradient_to"), color)
                : null;
            String texture = getString(map, "texture", null);
            int textureWidth = getInt(map, "texture_width", 256);
            int textureHeight = getInt(map, "texture_height", 256);
            return new UiBackground(color, gradientTo, texture, textureWidth, textureHeight);
        }
    }

    static final class UiElement {
        enum Type {
            TEXT,
            IMAGE,
            RECT,
            PROGRESS,
            SCROLL,
            SLOT,
            ENTITY
        }

        String id;
        Type type;
        UiValue x;
        UiValue y;
        int width;
        int height;
        UiAnchor anchor;
        UiAnchorAxis anchorAxis;
        int z;
        boolean visible;
        Map<String, String> actions;

        String text;
        float scale;
        boolean shadow;
        String align;
        int color;
        float opacity;
        String font;

        String texture;
        int u;
        int v;
        int textureWidth;
        int textureHeight;

        int fillColor;
        int backgroundColor;
        String mode;
        float value;
        float max;
        int durationMs;

        List<UiElement> children;
        String scrollDirection;
        float scrollStep;
        float scrollX;
        float scrollY;
        int slotIndex;
        boolean hoverMask;
        String targetType;
        String playerName;
        String entityUuid;
        boolean lookAtMouse;
        float entityScale;

        private UiElement(String id, Type type, UiValue x, UiValue y, int width, int height, UiAnchor anchor,
                          UiAnchorAxis anchorAxis, int z, boolean visible, Map<String, String> actions,
                          String text, float scale, boolean shadow, String align, int color, float opacity, String font, String texture,
                          int u, int v, int textureWidth, int textureHeight, int fillColor, int backgroundColor,
                          String mode, float value, float max, int durationMs, List<UiElement> children,
                          String scrollDirection, float scrollStep, float scrollX, float scrollY, int slotIndex,
                          boolean hoverMask, String targetType, String playerName, String entityUuid,
                          boolean lookAtMouse, float entityScale) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.anchor = anchor;
            this.anchorAxis = anchorAxis;
            this.z = z;
            this.visible = visible;
            this.actions = actions;
            this.text = text;
            this.scale = scale;
            this.shadow = shadow;
            this.align = align;
            this.color = color;
            this.opacity = opacity;
            this.font = font;
            this.texture = texture;
            this.u = u;
            this.v = v;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.fillColor = fillColor;
            this.backgroundColor = backgroundColor;
            this.mode = mode;
            this.value = value;
            this.max = max;
            this.durationMs = durationMs;
            this.children = children;
            this.scrollDirection = scrollDirection;
            this.scrollStep = scrollStep;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.slotIndex = slotIndex;
            this.hoverMask = hoverMask;
            this.targetType = targetType;
            this.playerName = playerName;
            this.entityUuid = entityUuid;
            this.lookAtMouse = lookAtMouse;
            this.entityScale = entityScale;
        }

        static UiElement fromMap(Map<String, Object> map) {
            String id = getString(map, "id", "element");
            Type type = parseType(map.get("type"));
            UiValue x = UiValue.of(map.get("x"), 0);
            UiValue y = UiValue.of(map.get("y"), 0);
            int width = getInt(map, "width", 0);
            int height = getInt(map, "height", 0);
            UiAnchor anchor = UiAnchor.from(map.get("anchor"));

            // Support separate anchor_x and anchor_y for more flexible positioning
            UiAnchorAxis anchorAxis;
            if (map.containsKey("anchor_x") || map.containsKey("anchor_y")) {
                anchorAxis = UiAnchorAxis.from(map.get("anchor_x"), map.get("anchor_y"));
            } else {
                anchorAxis = UiAnchorAxis.fromLegacy(anchor);
            }

            int z = getInt(map, "z", 0);
            boolean visible = getBoolean(map, "visible", true);
            Map<String, String> actions = parseActions(getMap(map, "actions"));

            String text = getString(map, "text", "");
            float scale = getFloat(map, "scale", 1.0f);
            boolean shadow = getBoolean(map, "shadow", false);
            String align = getString(map, "align", "left");
            int color = parseColor(map.get("color"), 0xFFFFFFFF);
            float opacity = getFloat(map, "opacity", 1.0f);
            String font = getString(map, "font", "original");

            String texture = getString(map, "texture", null);
            int u = getInt(map, "u", 0);
            int v = getInt(map, "v", 0);
            int textureWidth = getInt(map, "texture_width", 256);
            int textureHeight = getInt(map, "texture_height", 256);

            int fillColor = parseColor(map.get("fill_color"), 0xFF5BD6FF);
            int backgroundColor = parseColor(map.get("bg_color"), 0x00000000);
            String mode = getString(map, "mode", "static");
            float value = getFloat(map, "value", 0.0f);
            float max = getFloat(map, "max", 1.0f);
            int durationMs = getInt(map, "duration_ms", 2000);
            List<UiElement> children = parseElements(getList(map, "children"));
            String scrollDirection = getString(map, "scroll_direction", "vertical");
            float scrollStep = getFloat(map, "scroll_step", 12.0f);
            float scrollX = getFloat(map, "scroll_x", 0.0f);
            float scrollY = getFloat(map, "scroll_y", 0.0f);
            int slotIndex = getInt(map, "slot", -1);
            boolean hoverMask = getBoolean(map, "hover_mask", getBoolean(map, "slot_hover", false));
            String targetType = getString(map, "target_type", getString(map, "targetType", getString(map, "target", "player")));
            String playerName = getString(map, "player_name", getString(map, "playerName", ""));
            String entityUuid = getString(map, "entity_uuid", getString(map, "target_uuid", getString(map, "uuid", "")));
            boolean lookAtMouse = getBoolean(map, "look_at_mouse", getBoolean(map, "lookAtMouse", true));
            boolean scaleProvided = map.containsKey("scale");
            float entityScale = getFloat(map, "entity_scale", scaleProvided ? scale : 0.0f);

            return new UiElement(id, type, x, y, width, height, anchor, anchorAxis, z, visible, actions, text, scale, shadow, align,
                color, opacity, font, texture, u, v, textureWidth, textureHeight, fillColor, backgroundColor, mode,
                value, max, durationMs, children, scrollDirection, scrollStep, scrollX, scrollY, slotIndex, hoverMask,
                targetType, playerName, entityUuid, lookAtMouse, entityScale);
        }

        private static Type parseType(Object raw) {
            if (raw == null) {
                return Type.RECT;
            }
            String value = raw.toString().trim().toUpperCase();
            if ("SCROLL_LIST".equals(value)) {
                return Type.SCROLL;
            }
            try {
                return Type.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return Type.RECT;
            }
        }
    }

    static final class UiAnimation {
        enum Target {
            SCREEN,
            ELEMENT
        }

        final String id;
        final Target target;
        final String elementId;
        final int durationMs;
        final boolean loop;
        final float scaleFrom;
        final float scaleTo;
        final float alphaFrom;
        final float alphaTo;
        final float translateXFrom;
        final float translateYFrom;
        final float translateXTo;
        final float translateYTo;
        final float rotateFrom;
        final float rotateTo;
        final float pivotX;
        final float pivotY;

        private UiAnimation(String id, Target target, String elementId, int durationMs, boolean loop, float scaleFrom,
                            float scaleTo, float alphaFrom, float alphaTo, float translateXFrom, float translateYFrom,
                            float translateXTo, float translateYTo, float rotateFrom, float rotateTo,
                            float pivotX, float pivotY) {
            this.id = id;
            this.target = target;
            this.elementId = elementId;
            this.durationMs = durationMs;
            this.loop = loop;
            this.scaleFrom = scaleFrom;
            this.scaleTo = scaleTo;
            this.alphaFrom = alphaFrom;
            this.alphaTo = alphaTo;
            this.translateXFrom = translateXFrom;
            this.translateYFrom = translateYFrom;
            this.translateXTo = translateXTo;
            this.translateYTo = translateYTo;
            this.rotateFrom = rotateFrom;
            this.rotateTo = rotateTo;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }

        static UiAnimation fromMap(Map<String, Object> map) {
            if (map.isEmpty()) {
                return null;
            }
            String id = getString(map, "id", "");
            String targetRaw = getString(map, "target", "screen");
            Target target = "screen".equalsIgnoreCase(targetRaw) ? Target.SCREEN : Target.ELEMENT;
            String elementId = getString(map, "element", getString(map, "element_id", ""));
            int durationMs = getInt(map, "duration_ms", 600);
            boolean loop = getBoolean(map, "loop", false);
            float scaleFrom = getFloat(map, "scale_from", 1.0f);
            float scaleTo = getFloat(map, "scale_to", 1.0f);
            float alphaFrom = getFloat(map, "alpha_from", 1.0f);
            float alphaTo = getFloat(map, "alpha_to", 1.0f);
            float translateXFrom = getFloat(map, "translate_x_from", 0.0f);
            float translateYFrom = getFloat(map, "translate_y_from", 0.0f);
            float translateXTo = getFloat(map, "translate_x_to", 0.0f);
            float translateYTo = getFloat(map, "translate_y_to", 0.0f);
            float rotateFrom = getFloat(map, "rotate_from", 0.0f);
            float rotateTo = getFloat(map, "rotate_to", 0.0f);
            float pivotX = getFloat(map, "pivot_x", 0.5f);
            float pivotY = getFloat(map, "pivot_y", 0.5f);
            return new UiAnimation(id, target, elementId, durationMs, loop, scaleFrom, scaleTo, alphaFrom, alphaTo,
                translateXFrom, translateYFrom, translateXTo, translateYTo, rotateFrom, rotateTo, pivotX, pivotY);
        }
    }

    private static String getString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static int getInt(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float getFloat(Map<String, Object> map, String key, float fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
    }

    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) raw;
            return casted;
        }
        return Map.of();
    }

    private static Map<String, String> parseActions(Map<String, Object> map) {
        if (map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> actions = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            actions.put(entry.getKey().toLowerCase(), entry.getValue().toString());
        }
        return Collections.unmodifiableMap(actions);
    }

    private static List<Object> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> raw) {
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) raw;
            return casted;
        }
        return List.of();
    }

    private static List<String> getStringList(Map<String, Object> map, String key) {
        List<Object> values = getList(map, key);
        if (values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            result.add(value.toString());
        }
        return Collections.unmodifiableList(result);
    }

    static int parseColor(Object raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String text = raw.toString().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        try {
            long value = Long.parseLong(text, 16);
            if (text.length() <= 6) {
                return (int) (0xFF000000L | value);
            }
            return (int) value;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
