package org.test.magicmod.model;

public record RuntimeAnimationKeyframe(float timeSeconds, float x, float y, float z) {
    public RuntimeAnimationKeyframe {
        if (timeSeconds < 0.0f) {
            throw new IllegalArgumentException("Animation keyframe time cannot be negative");
        }
    }

    public static RuntimeAnimationKeyframe lerp(RuntimeAnimationKeyframe from, RuntimeAnimationKeyframe to, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        return new RuntimeAnimationKeyframe(
            from.timeSeconds + (to.timeSeconds - from.timeSeconds) * clamped,
            from.x + (to.x - from.x) * clamped,
            from.y + (to.y - from.y) * clamped,
            from.z + (to.z - from.z) * clamped
        );
    }
}
