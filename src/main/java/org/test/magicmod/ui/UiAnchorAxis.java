package org.test.magicmod.ui;

/**
 * Separate anchor control for X and Y axes, allowing more flexible UI positioning.
 */
public class UiAnchorAxis {

    public enum Horizontal {
        LEFT,
        CENTER,
        RIGHT;

        static Horizontal from(Object raw) {
            if (raw == null) {
                return LEFT;
            }
            String value = raw.toString().trim().toUpperCase().replace('-', '_');
            try {
                return Horizontal.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return LEFT;
            }
        }

        int apply(int x, int elementWidth) {
            return switch (this) {
                case CENTER -> x - elementWidth / 2;
                case RIGHT -> x - elementWidth;
                default -> x;
            };
        }
    }

    public enum Vertical {
        TOP,
        CENTER,
        BOTTOM;

        static Vertical from(Object raw) {
            if (raw == null) {
                return TOP;
            }
            String value = raw.toString().trim().toUpperCase().replace('-', '_');
            try {
                return Vertical.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return TOP;
            }
        }

        int apply(int y, int elementHeight) {
            return switch (this) {
                case CENTER -> y - elementHeight / 2;
                case BOTTOM -> y - elementHeight;
                default -> y;
            };
        }
    }

    private final Horizontal horizontal;
    private final Vertical vertical;

    public UiAnchorAxis(Horizontal horizontal, Vertical vertical) {
        this.horizontal = horizontal != null ? horizontal : Horizontal.LEFT;
        this.vertical = vertical != null ? vertical : Vertical.TOP;
    }

    public static UiAnchorAxis from(Object anchorX, Object anchorY) {
        Horizontal h = Horizontal.from(anchorX);
        Vertical v = Vertical.from(anchorY);
        return new UiAnchorAxis(h, v);
    }

    public static UiAnchorAxis fromLegacy(UiAnchor anchor) {
        if (anchor == null) {
            return new UiAnchorAxis(Horizontal.LEFT, Vertical.TOP);
        }

        Horizontal h = switch (anchor) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> Horizontal.CENTER;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> Horizontal.RIGHT;
            default -> Horizontal.LEFT;
        };

        Vertical v = switch (anchor) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> Vertical.CENTER;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> Vertical.BOTTOM;
            default -> Vertical.TOP;
        };

        return new UiAnchorAxis(h, v);
    }

    public int applyX(int x, int elementWidth) {
        return horizontal.apply(x, elementWidth);
    }

    public int applyY(int y, int elementHeight) {
        return vertical.apply(y, elementHeight);
    }
}
