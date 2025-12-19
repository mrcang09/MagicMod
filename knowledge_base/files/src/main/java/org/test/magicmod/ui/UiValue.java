package org.test.magicmod.ui;

final class UiValue {
    private final float value;
    private final boolean percent;
    private final String expression;

    private UiValue(float value, boolean percent, String expression) {
        this.value = value;
        this.percent = percent;
        this.expression = expression;
    }

    static UiValue of(Object raw, float fallback) {
        if (raw == null) {
            return new UiValue(fallback, false, null);
        }
        if (raw instanceof Number number) {
            return new UiValue(number.floatValue(), false, null);
        }
        String text = raw.toString().trim();
        if (text.endsWith("%")) {
            String percentText = text.substring(0, text.length() - 1).trim();
            float pct = parseFloat(percentText, fallback);
            return new UiValue(pct / 100.0f, true, null);
        }
        if (isPlainNumber(text)) {
            return new UiValue(parseFloat(text, fallback), false, null);
        }
        return new UiValue(fallback, false, text);
    }

    int resolve(int width, int height, int size) {
        if (expression != null) {
            return Math.round(evaluateExpression(expression, width, height, value));
        }
        if (percent) {
            return Math.round(size * value);
        }
        return Math.round(value);
    }

    private static boolean isPlainNumber(String text) {
        return text.matches("[-+]?\\d*(?:\\.\\d+)?");
    }

    private static float parseFloat(String text, float fallback) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float evaluateExpression(String expression, int width, int height, float fallback) {
        try {
            return new ExpressionParser(expression, width, height).parse();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static final class ExpressionParser {
        private final String source;
        private final int width;
        private final int height;
        private int index;

        private ExpressionParser(String source, int width, int height) {
            this.source = source;
            this.width = width;
            this.height = height;
            this.index = 0;
        }

        private float parse() {
            float value = parseExpression();
            skipWhitespace();
            return value;
        }

        private float parseExpression() {
            float value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        private float parseTerm() {
            float value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    value /= parseFactor();
                } else {
                    break;
                }
            }
            return value;
        }

        private float parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                float value = parseExpression();
                match(')');
                return value;
            }
            if (peek() == 'w' || peek() == 'W') {
                index++;
                return width;
            }
            if (peek() == 'h' || peek() == 'H') {
                index++;
                return height;
            }
            return parseNumber();
        }

        private float parseNumber() {
            skipWhitespace();
            int start = index;
            boolean dotSeen = false;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (Character.isDigit(ch)) {
                    index++;
                } else if (ch == '.' && !dotSeen) {
                    dotSeen = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected number at " + index);
            }
            return Float.parseFloat(source.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean match(char expected) {
            if (peek() == expected) {
                index++;
                return true;
            }
            return false;
        }

        private char peek() {
            if (index >= source.length()) {
                return '\0';
            }
            return source.charAt(index);
        }
    }
}
