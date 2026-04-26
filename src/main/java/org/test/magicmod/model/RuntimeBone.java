package org.test.magicmod.model;

public record RuntimeBone(
    String name,
    int parentIndex,
    float pivotX,
    float pivotY,
    float pivotZ
) {
    public RuntimeBone {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bone name cannot be blank");
        }
        if (parentIndex < -1) {
            throw new IllegalArgumentException("Bone parent index cannot be less than -1");
        }
    }
}
