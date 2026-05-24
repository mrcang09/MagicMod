package org.test.magicmod.model;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public final class RuntimeModel implements AutoCloseable {
    private final Identifier id;
    private final RuntimeSkeleton skeleton;
    private final List<RuntimeMeshPart> meshParts;
    private final Map<String, RuntimeAnimationClip> animations;
    private final float width;
    private final float height;

    public RuntimeModel(
        Identifier id,
        RuntimeSkeleton skeleton,
        List<RuntimeMeshPart> meshParts,
        Map<String, RuntimeAnimationClip> animations,
        float width,
        float height
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Model id cannot be null");
        }
        if (meshParts == null || meshParts.isEmpty()) {
            throw new IllegalArgumentException("Runtime model must contain at least one mesh part");
        }
        if (animations == null || animations.isEmpty()) {
            throw new IllegalArgumentException("Runtime model must contain at least one animation");
        }
        this.id = id;
        this.skeleton = skeleton == null ? RuntimeSkeleton.EMPTY : skeleton;
        this.meshParts = List.copyOf(meshParts);
        this.animations = Map.copyOf(animations);
        this.width = Math.max(0.01f, width);
        this.height = Math.max(0.01f, height);
    }

    public Identifier id() {
        return id;
    }

    public RuntimeSkeleton skeleton() {
        return skeleton;
    }

    public List<RuntimeMeshPart> meshParts() {
        return meshParts;
    }

    public Map<String, RuntimeAnimationClip> animations() {
        return animations;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public RuntimeAnimationClip animationOrDefault(String requested) {
        RuntimeAnimationClip clip = animations.get(resolveAnimationName(requested));
        if (clip != null) {
            return clip;
        }
        RuntimeAnimationClip idle = animations.get("idle");
        if (idle != null) {
            return idle;
        }
        return animations.values().iterator().next();
    }

    public String resolveAnimationName(String requested) {
        String matched = findAnimationName(requested);
        if (matched != null) {
            return matched;
        }
        if (animations.containsKey("idle")) {
            return "idle";
        }
        for (String name : animations.keySet()) {
            if (name.endsWith(".idle")) {
                return name;
            }
        }
        return animations.keySet().iterator().next();
    }

    public String findAnimationName(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        RuntimeAnimationClip direct = animations.get(requested);
        if (direct != null) {
            return direct.name();
        }
        for (String name : animations.keySet()) {
            if (name.endsWith("." + requested)) {
                return name;
            }
        }
        return null;
    }

    @Override
    public void close() {
        for (RuntimeMeshPart meshPart : meshParts) {
            meshPart.close();
        }
    }
}
