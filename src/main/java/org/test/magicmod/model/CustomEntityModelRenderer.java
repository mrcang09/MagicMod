package org.test.magicmod.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Vector3f;

public final class CustomEntityModelRenderer {
    private CustomEntityModelRenderer() {
    }

    public static void submit(
        ResolvedModelReplacement replacement,
        LivingEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector nodeCollector
    ) {
        RuntimeModel model = replacement.model();
        RuntimeAnimationClip animation = replacement.animation();
        float time = replacement.instance().animationTimeSeconds(animation);
        String animationName = animation.name().toLowerCase();
        float width = Math.max(0.25f, Math.max(model.width(), state.boundingBoxWidth));
        float height = Math.max(0.25f, Math.max(model.height(), state.boundingBoxHeight));
        boolean useLegacyWholeModelAnimation = !animation.hasBoneAnimations();
        float pulseScale = useLegacyWholeModelAnimation && animationName.contains("pulse")
            ? 1.0f + (float) Math.sin(time * Math.PI * 2.0f) * 0.12f
            : 1.0f;
        float spinDegrees = useLegacyWholeModelAnimation && animationName.contains("spin")
            ? (time / animation.lengthSeconds()) * 360.0f
            : state.yRot;

        poseStack.pushPose();
        poseStack.translate(0.0f, 0.01f, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - spinDegrees));
        poseStack.scale(width * pulseScale, height, width * pulseScale);

        for (RuntimeMeshPart meshPart : model.meshParts()) {
            nodeCollector.submitCustomGeometry(poseStack, renderType(meshPart), (pose, consumer) -> {
                Vector3f position = new Vector3f();
                Vector3f normal = new Vector3f();
                for (RuntimeQuad quad : meshPart.quads()) {
                    emitVertex(consumer, pose, replacement.instance(), quad.a(), position, normal);
                    emitVertex(consumer, pose, replacement.instance(), quad.b(), position, normal);
                    emitVertex(consumer, pose, replacement.instance(), quad.c(), position, normal);
                    emitVertex(consumer, pose, replacement.instance(), quad.d(), position, normal);
                }
            });
        }

        poseStack.popPose();
    }

    private static RenderType renderType(RuntimeMeshPart meshPart) {
        if (meshPart.renderMode() == ModelRenderMode.DEBUG_SOLID) {
            return RenderTypes.debugFilledBox();
        }
        if (meshPart.texture() == null) {
            return RenderTypes.debugFilledBox();
        }
        if (meshPart.renderMode() == ModelRenderMode.SOLID) {
            return RenderTypes.entitySolid(meshPart.texture());
        }
        if (meshPart.renderMode() == ModelRenderMode.TRANSLUCENT) {
            return RenderTypes.entityTranslucent(meshPart.texture());
        }
        return RenderTypes.entityCutoutNoCull(meshPart.texture());
    }

    private static void emitVertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        ModelRuntimeInstance instance,
        RuntimeVertex vertex,
        Vector3f position,
        Vector3f normal
    ) {
        instance.transformPosition(vertex, position);
        instance.transformNormal(vertex, normal);
        consumer.addVertex(pose, position.x(), position.y(), position.z())
            .setColor(vertex.red(), vertex.green(), vertex.blue(), vertex.alpha())
            .setUv(vertex.u(), vertex.v())
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(0xF000F0)
            .setNormal(pose, normal.x(), normal.y(), normal.z());
    }
}
