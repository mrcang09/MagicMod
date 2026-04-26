package org.test.magicmod.model;

import net.minecraft.resources.Identifier;

public record ModelReplacementRule(
    Identifier ruleId,
    ModelReplacementTarget target,
    ModelAssetSpec modelSpec,
    String animation,
    int priority,
    String source
) {
    public ModelReplacementRule(Identifier targetEntityType, ModelAssetSpec modelSpec, String animation) {
        this(
            Identifier.fromNamespaceAndPath("magicmod", "manual/" + targetEntityType.getNamespace() + "/" + targetEntityType.getPath()),
            ModelReplacementTarget.entityType(targetEntityType),
            modelSpec,
            animation,
            Integer.MAX_VALUE,
            "command"
        );
    }

    public ModelReplacementRule {
        if (ruleId == null) {
            throw new IllegalArgumentException("Rule id cannot be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target cannot be null");
        }
        if (modelSpec == null) {
            throw new IllegalArgumentException("Model asset spec cannot be null");
        }
        if (animation == null || animation.isBlank()) {
            animation = "idle";
        }
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    public Identifier targetEntityType() {
        return target.entityType();
    }

    public String targetKey() {
        return target.ruleKey();
    }

    public ModelReplacementRule withAnimation(String nextAnimation) {
        return new ModelReplacementRule(ruleId, target, modelSpec, nextAnimation, priority, source);
    }

    public ModelReplacementRule withSource(String nextSource) {
        return new ModelReplacementRule(ruleId, target, modelSpec, animation, priority, nextSource);
    }

    public String toDebugLine() {
        return "id=" + ruleId
            + ", target=" + target.displayName()
            + ", model=" + modelSpec.displayName()
            + ", animation=" + animation
            + ", priority=" + priority
            + ", source=" + source;
    }
}
