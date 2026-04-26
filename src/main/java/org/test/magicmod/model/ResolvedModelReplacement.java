package org.test.magicmod.model;

public record ResolvedModelReplacement(
    ModelReplacementRule rule,
    RuntimeModel model,
    ModelRuntimeInstance instance,
    RuntimeAnimationClip animation
) {
}
