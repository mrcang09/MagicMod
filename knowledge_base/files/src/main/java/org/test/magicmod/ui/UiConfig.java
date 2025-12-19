package org.test.magicmod.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class UiConfig {
    public final String id;
    public final String title;
    public final boolean replaceVanilla;
    public final List<String> matchTitles;
    public final Map<String, String> events;
    public final UiBackground background;
    public final List<UiElement> elements;

    private UiConfig(String id, String title, boolean replaceVanilla, List<String> matchTitles,
                     Map<String, String> events, UiBackground background, List<UiElement> elements) {
        this.id = id;
        this.title = title;
        this.replaceVanilla = replaceVanilla;
        this.matchTitles = matchTitles;
        this.events = events;
        this.background = background;
        this.elements = elements;
    }

    static UiConfig fromMap(Map<String, Object> root) {
        String id = getString(root, "id", "ui");
        String title = getString(root, "title", id);
        boolean replaceVanilla = getBoolean(root, "replace_vanilla", false);
        List<String> matchTitles = getStringList(root, "match_titles");
        Map<String, String> events = parseActions(getMap(root, "events"));
        UiBackground background = UiBackground.fromMap(getMap(root, "background"));
        List<UiElement> elements = parseElements(getList(root, "elements"));
        return new UiConfig(id, title, replaceVanilla, matchTitles, events, background, elements);
    }

    private static List<UiElement> parseElements(List<Object> rawElements) {
        if (rawElements.isEmpty()) {
            return List.of();
        }
        List<UiElement> elements = new ArrayList<>();
        for (Object rawElement : rawElements) {
            if (rawElement instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> elementMap = (Map<String, Object>) map;
                elements.add(UiElement.fromMap(elementMap));
            }
        }
        elements.sort((left, right) -> Integer.compare(left.z, right.z));
        return Collections.unmodifiableList(elements);
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
            SLOT
        }

        final String id;
        final Type type;
        final UiValue x;
        final UiValue y;
        final int width;
        final int height;
        final UiAnchor anchor;
        final int z;
        final boolean visible;
        final Map<String, String> actions;

        final String text;
        final float scale;
        final boolean shadow;
        final String align;
        final int color;
        final String font;

        final String texture;
        final int u;
        final int v;
        final int textureWidth;
        final int textureHeight;

        final int fillColor;
        final int backgroundColor;
        final String mode;
        final float value;
        final float max;
        final int durationMs;

        final List<UiElement> children;
        final String scrollDirection;
        final float scrollStep;
        final float scrollX;
        final float scrollY;
        final int slotIndex;
        final boolean hoverMask;

        private UiElement(String id, Type type, UiValue x, UiValue y, int width, int height, UiAnchor anchor, int z,
                          boolean visible, Map<String, String> actions,
                          String text, float scale, boolean shadow, String align, int color, String font, String texture,
                          int u, int v, int textureWidth, int textureHeight, int fillColor, int backgroundColor,
                          String mode, float value, float max, int durationMs, List<UiElement> children,
                          String scrollDirection, float scrollStep, float scrollX, float scrollY, int slotIndex,
                          boolean hoverMask) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.anchor = anchor;
            this.z = z;
            this.visible = visible;
            this.actions = actions;
            this.text = text;
            this.scale = scale;
            this.shadow = shadow;
            this.align = align;
            this.color = color;
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
        }

        static UiElement fromMap(Map<String, Object> map) {
            String id = getString(map, "id", "element");
            Type type = parseType(map.get("type"));
            UiValue x = UiValue.of(map.get("x"), 0);
            UiValue y = UiValue.of(map.get("y"), 0);
            int width = getInt(map, "width", 0);
            int height = getInt(map, "height", 0);
            UiAnchor anchor = UiAnchor.from(map.get("anchor"));
            int z = getInt(map, "z", 0);
            boolean visible = getBoolean(map, "visible", true);
            Map<String, String> actions = parseActions(getMap(map, "actions"));

            String text = getString(map, "text", "");
            float scale = getFloat(map, "scale", 1.0f);
            boolean shadow = getBoolean(map, "shadow", false);
            String align = getString(map, "align", "left");
            int color = parseColor(map.get("color"), 0xFFFFFFFF);
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

            return new UiElement(id, type, x, y, width, height, anchor, z, visible, actions, text, scale, shadow, align,
                color, font, texture, u, v, textureWidth, textureHeight, fillColor, backgroundColor, mode, value, max,
                durationMs, children, scrollDirection, scrollStep, scrollX, scrollY, slotIndex, hoverMask);
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
