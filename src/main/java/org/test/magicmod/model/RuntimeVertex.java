package org.test.magicmod.model;

public record RuntimeVertex(
    float x,
    float y,
    float z,
    float u,
    float v,
    float normalX,
    float normalY,
    float normalZ,
    int boneIndex,
    int red,
    int green,
    int blue,
    int alpha
) {
    public RuntimeVertex(float x, float y, float z, int red, int green, int blue, int alpha) {
        this(x, y, z, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0, red, green, blue, alpha);
    }

    public RuntimeVertex {
        if (boneIndex < 0) {
            throw new IllegalArgumentException("Bone index cannot be negative");
        }
        red = clampColor(red);
        green = clampColor(green);
        blue = clampColor(blue);
        alpha = clampColor(alpha);
    }

    public RuntimeVertex withPosition(float nextX, float nextY, float nextZ) {
        return new RuntimeVertex(nextX, nextY, nextZ, u, v, normalX, normalY, normalZ, boneIndex, red, green, blue, alpha);
    }

    private static int clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
