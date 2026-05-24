package org.test.magicmod.ui;

enum UiAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    static UiAnchor from(Object raw) {
        if (raw == null) {
            return TOP_LEFT;
        }
        String value = raw.toString().trim().toUpperCase().replace('-', '_');
        try {
            return UiAnchor.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return TOP_LEFT;
        }
    }

    int applyX(int x, int elementWidth) {
        return switch (this) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> x - elementWidth / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> x - elementWidth;
            default -> x;
        };
    }

    int applyY(int y, int elementHeight) {
        return switch (this) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> y - elementHeight / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> y - elementHeight;
            default -> y;
        };
    }
}
