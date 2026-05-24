package org.test.magicmod.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RuntimeSkeleton {
    public static final RuntimeSkeleton EMPTY = new RuntimeSkeleton(List.of());

    private final List<RuntimeBone> bones;
    private final Map<String, Integer> boneIndices;

    public RuntimeSkeleton(List<RuntimeBone> bones) {
        if (bones == null) {
            bones = List.of();
        }
        if (bones.size() > 128) {
            throw new IllegalArgumentException("First runtime version supports at most 128 bones");
        }
        this.bones = List.copyOf(bones);
        this.boneIndices = this.bones.stream()
            .collect(Collectors.toUnmodifiableMap(RuntimeBone::name, this.bones::indexOf, (first, ignored) -> first));
    }

    public int boneCount() {
        return bones.size();
    }

    public List<RuntimeBone> bones() {
        return bones;
    }

    public RuntimeBone bone(int index) {
        return bones.get(index);
    }

    public int indexOf(String boneName) {
        Integer index = boneIndices.get(boneName);
        return index == null ? -1 : index;
    }
}
