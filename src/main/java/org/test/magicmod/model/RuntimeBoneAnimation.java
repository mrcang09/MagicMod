package org.test.magicmod.model;

import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

public record RuntimeBoneAnimation(
    List<RuntimeAnimationKeyframe> positions,
    List<RuntimeAnimationKeyframe> rotations,
    List<RuntimeAnimationKeyframe> scales
) {
    private static final RuntimeAnimationKeyframe ZERO = new RuntimeAnimationKeyframe(0.0f, 0.0f, 0.0f, 0.0f);
    private static final RuntimeAnimationKeyframe ONE = new RuntimeAnimationKeyframe(0.0f, 1.0f, 1.0f, 1.0f);

    public RuntimeBoneAnimation {
        positions = sortedCopy(positions);
        rotations = sortedCopy(rotations);
        scales = sortedCopy(scales);
    }

    public static RuntimeBoneAnimation empty() {
        return new RuntimeBoneAnimation(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return positions.isEmpty() && rotations.isEmpty() && scales.isEmpty();
    }

    public RuntimeAnimationKeyframe positionAt(float timeSeconds) {
        return sample(positions, timeSeconds, ZERO);
    }

    public RuntimeAnimationKeyframe rotationAt(float timeSeconds) {
        return sample(rotations, timeSeconds, ZERO);
    }

    public RuntimeAnimationKeyframe scaleAt(float timeSeconds) {
        return sample(scales, timeSeconds, ONE);
    }

    public void samplePosition(float timeSeconds, Vector3f destination) {
        sampleInto(positions, timeSeconds, ZERO, destination);
    }

    public void sampleRotation(float timeSeconds, Vector3f destination) {
        sampleInto(rotations, timeSeconds, ZERO, destination);
    }

    public void sampleScale(float timeSeconds, Vector3f destination) {
        sampleInto(scales, timeSeconds, ONE, destination);
    }

    private static List<RuntimeAnimationKeyframe> sortedCopy(List<RuntimeAnimationKeyframe> frames) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        return frames.stream()
            .sorted(Comparator.comparing(RuntimeAnimationKeyframe::timeSeconds))
            .toList();
    }

    private static RuntimeAnimationKeyframe sample(
        List<RuntimeAnimationKeyframe> frames,
        float timeSeconds,
        RuntimeAnimationKeyframe fallback
    ) {
        if (frames.isEmpty()) {
            return fallback;
        }
        if (timeSeconds <= frames.get(0).timeSeconds()) {
            return frames.get(0);
        }
        RuntimeAnimationKeyframe previous = frames.get(0);
        for (int index = 1; index < frames.size(); index++) {
            RuntimeAnimationKeyframe next = frames.get(index);
            if (timeSeconds <= next.timeSeconds()) {
                float duration = Math.max(0.0001f, next.timeSeconds() - previous.timeSeconds());
                return RuntimeAnimationKeyframe.lerp(previous, next, (timeSeconds - previous.timeSeconds()) / duration);
            }
            previous = next;
        }
        return frames.get(frames.size() - 1);
    }

    private static void sampleInto(
        List<RuntimeAnimationKeyframe> frames,
        float timeSeconds,
        RuntimeAnimationKeyframe fallback,
        Vector3f destination
    ) {
        if (frames.isEmpty()) {
            destination.set(fallback.x(), fallback.y(), fallback.z());
            return;
        }
        if (timeSeconds <= frames.get(0).timeSeconds()) {
            RuntimeAnimationKeyframe frame = frames.get(0);
            destination.set(frame.x(), frame.y(), frame.z());
            return;
        }
        RuntimeAnimationKeyframe previous = frames.get(0);
        for (int index = 1; index < frames.size(); index++) {
            RuntimeAnimationKeyframe next = frames.get(index);
            if (timeSeconds <= next.timeSeconds()) {
                float duration = Math.max(0.0001f, next.timeSeconds() - previous.timeSeconds());
                float progress = Math.max(0.0f, Math.min(1.0f, (timeSeconds - previous.timeSeconds()) / duration));
                destination.set(
                    previous.x() + (next.x() - previous.x()) * progress,
                    previous.y() + (next.y() - previous.y()) * progress,
                    previous.z() + (next.z() - previous.z()) * progress
                );
                return;
            }
            previous = next;
        }
        RuntimeAnimationKeyframe frame = frames.get(frames.size() - 1);
        destination.set(frame.x(), frame.y(), frame.z());
    }
}
