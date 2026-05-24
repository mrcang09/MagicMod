package org.test.magicmod.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.test.magicmod.model.bedrock.BedrockModelLoader;
import org.test.magicmod.model.fbx.FbxModelLoader;
import org.test.magicmod.model.mmesh.MmeshModelLoader;
import org.test.magicmod.Magicmod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelCache {
    public static final Identifier TEST_CUBE_ID = ModelAssetSpec.TEST_CUBE_ID;

    private static final Map<ModelAssetSpec, RuntimeModel> MODELS = new ConcurrentHashMap<>();
    private static int reloadVersion;

    private ModelCache() {
    }

    public static RuntimeModel getOrLoad(ModelAssetSpec spec) {
        return MODELS.computeIfAbsent(spec, ModelCache::loadModel);
    }

    public static RuntimeModel getOrLoad(Identifier modelId) {
        return getOrLoad(specFor(modelId));
    }

    public static ModelAssetSpec specFor(Identifier modelId) {
        if (TEST_CUBE_ID.equals(modelId)) {
            return ModelAssetSpec.TEST_CUBE;
        }
        throw new IllegalArgumentException("Unsupported runtime model id: " + modelId);
    }

    public static void clear() {
        for (RuntimeModel model : MODELS.values()) {
            try {
                model.close();
            } catch (Exception error) {
                Magicmod.LOGGER.warn("Failed to close runtime model {}", model.id(), error);
            }
        }
        MODELS.clear();
        reloadVersion++;
    }

    public static int loadedModelCount() {
        return MODELS.size();
    }

    public static int reloadVersion() {
        return reloadVersion;
    }

    public static int liveGpuBufferCount() {
        return GpuMeshBuffer.liveBufferCount();
    }

    private static RuntimeModel loadModel(ModelAssetSpec spec) {
        if (spec.format() == ModelAssetFormat.TEST) {
            return TestRuntimeModels.createTestCube(spec.modelId());
        }
        if (spec.format() == ModelAssetFormat.BEDROCK) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                throw new IllegalStateException("Minecraft client is not available for Bedrock model loading");
            }
            return BedrockModelLoader.load(spec, minecraft.getResourceManager());
        }
        if (spec.format() == ModelAssetFormat.FBX) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                throw new IllegalStateException("Minecraft client is not available for FBX model loading");
            }
            return FbxModelLoader.load(spec, minecraft.getResourceManager());
        }
        if (spec.format() == ModelAssetFormat.MMESH) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                throw new IllegalStateException("Minecraft client is not available for mmesh model loading");
            }
            return MmeshModelLoader.load(spec, minecraft.getResourceManager());
        }
        throw new IllegalArgumentException("Unsupported runtime model format: " + spec.format());
    }
}
