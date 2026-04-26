package org.test.magicmod.model;

import java.util.Map;

public record RuntimeAnimationClip(
    String name,
    float lengthSeconds,
    boolean loop,
    Map<String, RuntimeBoneAnimation> boneAnimations
) {
    public RuntimeAnimationClip(String name, float lengthSeconds, boolean loop) {
        this(name, lengthSeconds, loop, Map.of());
    }

    public RuntimeAnimationClip {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Animation name cannot be blank");
        }
        if (lengthSeconds <= 0.0f) {
            throw new IllegalArgumentException("Animation length must be positive");
        }
        if (boneAnimations == null) {
            boneAnimations = Map.of();
        } else {
            boneAnimations = Map.copyOf(boneAnimations);
        }
    }

    public boolean hasBoneAnimations() {
        return !boneAnimations.isEmpty();
    }

    public RuntimeBoneAnimation boneAnimation(String boneName) {
        RuntimeBoneAnimation animation = boneAnimations.get(boneName);
        return animation == null ? RuntimeBoneAnimation.empty() : animation;
    }
}
