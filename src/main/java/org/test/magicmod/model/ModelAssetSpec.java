package org.test.magicmod.model;

import net.minecraft.resources.Identifier;
import org.test.magicmod.Magicmod;

public record ModelAssetSpec(
    ModelAssetFormat format,
    Identifier modelId,
    Identifier geometry,
    Identifier texture,
    Identifier animation,
    ModelRenderMode renderMode
) {
    public static final Identifier TEST_CUBE_ID = Identifier.fromNamespaceAndPath(Magicmod.MODID, "test_cube");
    public static final ModelAssetSpec TEST_CUBE = new ModelAssetSpec(
        ModelAssetFormat.TEST,
        TEST_CUBE_ID,
        null,
        null,
        null,
        ModelRenderMode.DEBUG_SOLID
    );

    public ModelAssetSpec {
        if (format == null) {
            throw new IllegalArgumentException("Model asset format cannot be null");
        }
        if (modelId == null) {
            throw new IllegalArgumentException("Model id cannot be null");
        }
        if (renderMode == null) {
            renderMode = ModelRenderMode.CUTOUT;
        }
        if (format == ModelAssetFormat.BEDROCK) {
            if (geometry == null) {
                throw new IllegalArgumentException("Bedrock model requires a geometry resource");
            }
            if (texture == null) {
                throw new IllegalArgumentException("Bedrock model requires a texture resource");
            }
        }
        if (format == ModelAssetFormat.FBX || format == ModelAssetFormat.MMESH) {
            if (geometry == null) {
                throw new IllegalArgumentException(format.name() + " model requires a source resource");
            }
            if (texture == null) {
                throw new IllegalArgumentException(format.name() + " model requires a texture resource");
            }
        }
    }

    public static ModelAssetSpec bedrock(Identifier geometry, Identifier texture, Identifier animation, ModelRenderMode renderMode) {
        return new ModelAssetSpec(
            ModelAssetFormat.BEDROCK,
            stableBedrockModelId(geometry, texture, animation),
            geometry,
            texture,
            animation,
            renderMode
        );
    }

    public static ModelAssetSpec fbx(Identifier source, Identifier texture, Identifier animation, ModelRenderMode renderMode) {
        return new ModelAssetSpec(
            ModelAssetFormat.FBX,
            stableModelId("fbx_runtime", source, texture, animation),
            source,
            texture,
            animation,
            renderMode
        );
    }

    public static ModelAssetSpec mmesh(Identifier source, Identifier texture, Identifier animation, ModelRenderMode renderMode) {
        return new ModelAssetSpec(
            ModelAssetFormat.MMESH,
            stableModelId("mmesh_runtime", source, texture, animation),
            source,
            texture,
            animation,
            renderMode
        );
    }

    public String displayName() {
        if (format == ModelAssetFormat.TEST) {
            return modelId.toString();
        }
        String value = format.name().toLowerCase() + " " + geometry + " " + texture;
        if (animation != null) {
            value += " " + animation;
        }
        return value;
    }

    private static Identifier stableBedrockModelId(Identifier geometry, Identifier texture, Identifier animation) {
        return stableModelId("bedrock_runtime", geometry, texture, animation);
    }

    private static Identifier stableModelId(String prefix, Identifier geometry, Identifier texture, Identifier animation) {
        String key = geometry + "|" + texture + "|" + (animation == null ? "" : animation.toString());
        String sanitized = Integer.toUnsignedString(key.hashCode(), 36);
        return Identifier.fromNamespaceAndPath(geometry.getNamespace(), prefix + "/" + sanitized);
    }
}
