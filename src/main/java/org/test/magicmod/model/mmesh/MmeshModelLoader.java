package org.test.magicmod.model.mmesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.ResourceManager;
import org.test.magicmod.model.ModelAssetSpec;
import org.test.magicmod.model.ModelRenderMode;
import org.test.magicmod.model.RuntimeAnimationClip;
import org.test.magicmod.model.RuntimeMeshPart;
import org.test.magicmod.model.RuntimeModel;
import org.test.magicmod.model.RuntimeQuad;
import org.test.magicmod.model.RuntimeSkeleton;
import org.test.magicmod.model.RuntimeVertex;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MmeshModelLoader {
    private MmeshModelLoader() {
    }

    public static RuntimeModel load(ModelAssetSpec spec, ResourceManager resourceManager) {
        if (spec.geometry() == null || spec.texture() == null) {
            throw new IllegalArgumentException("MMESH model spec requires source and texture resources");
        }

        try (BufferedReader reader = resourceManager.openAsReader(spec.geometry())) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<RuntimeMeshPart> meshParts = parseMeshParts(root, spec);
            Map<String, RuntimeAnimationClip> animations = parseAnimations(root);
            if (animations.isEmpty()) {
                animations = Map.of(
                    "idle", new RuntimeAnimationClip("idle", 2.0f, true),
                    "spin", new RuntimeAnimationClip("spin", 1.5f, true),
                    "pulse", new RuntimeAnimationClip("pulse", 1.0f, true)
                );
            }
            float width = floatValue(root, "width", 1.0f);
            float height = floatValue(root, "height", 1.0f);
            return new RuntimeModel(
                spec.modelId(),
                RuntimeSkeleton.EMPTY,
                meshParts,
                animations,
                width,
                height
            );
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed to load MMESH resource: " + spec.displayName(), error);
        }
    }

    private static List<RuntimeMeshPart> parseMeshParts(JsonObject root, ModelAssetSpec spec) throws IOException {
        JsonArray parts = array(root, "mesh_parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("MMESH requires a non-empty mesh_parts array");
        }

        List<RuntimeMeshPart> meshParts = new ArrayList<>();
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            JsonObject part = parts.get(partIndex).getAsJsonObject();
            String name = stringValue(part, "name", "part_" + partIndex);
            ModelRenderMode renderMode = renderModeValue(part, "render_mode", spec.renderMode());
            JsonArray vertices = array(part, "vertices");
            JsonArray quads = array(part, "quads");
            if (vertices == null || vertices.isEmpty() || quads == null || quads.isEmpty()) {
                throw new IOException("MMESH part " + name + " requires vertices and quads");
            }
            List<RuntimeVertex> runtimeVertices = new ArrayList<>();
            for (JsonElement vertex : vertices) {
                runtimeVertices.add(parseVertex(vertex.getAsJsonArray()));
            }
            List<RuntimeQuad> runtimeQuads = new ArrayList<>();
            for (JsonElement quadElement : quads) {
                JsonArray quad = quadElement.getAsJsonArray();
                if (quad.size() != 4) {
                    throw new IOException("MMESH quad must contain exactly four vertex indices");
                }
                runtimeQuads.add(new RuntimeQuad(
                    runtimeVertices.get(quad.get(0).getAsInt()),
                    runtimeVertices.get(quad.get(1).getAsInt()),
                    runtimeVertices.get(quad.get(2).getAsInt()),
                    runtimeVertices.get(quad.get(3).getAsInt())
                ));
            }
            meshParts.add(new RuntimeMeshPart(name, renderMode, spec.texture(), runtimeQuads));
        }
        return List.copyOf(meshParts);
    }

    private static RuntimeVertex parseVertex(JsonArray vertex) throws IOException {
        if (vertex.size() < 8) {
            throw new IOException("MMESH vertex requires at least x,y,z,u,v,nx,ny,nz");
        }
        int boneIndex = vertex.size() > 8 ? vertex.get(8).getAsInt() : 0;
        int red = vertex.size() > 9 ? vertex.get(9).getAsInt() : 255;
        int green = vertex.size() > 10 ? vertex.get(10).getAsInt() : 255;
        int blue = vertex.size() > 11 ? vertex.get(11).getAsInt() : 255;
        int alpha = vertex.size() > 12 ? vertex.get(12).getAsInt() : 255;
        return new RuntimeVertex(
            vertex.get(0).getAsFloat(),
            vertex.get(1).getAsFloat(),
            vertex.get(2).getAsFloat(),
            vertex.get(3).getAsFloat(),
            vertex.get(4).getAsFloat(),
            vertex.get(5).getAsFloat(),
            vertex.get(6).getAsFloat(),
            vertex.get(7).getAsFloat(),
            boneIndex,
            red,
            green,
            blue,
            alpha
        );
    }

    private static Map<String, RuntimeAnimationClip> parseAnimations(JsonObject root) {
        JsonArray animations = array(root, "animations");
        if (animations == null || animations.isEmpty()) {
            return Map.of();
        }
        Map<String, RuntimeAnimationClip> clips = new LinkedHashMap<>();
        for (JsonElement element : animations) {
            JsonObject clip = element.getAsJsonObject();
            String name = stringValue(clip, "name", "idle");
            float length = Math.max(0.05f, floatValue(clip, "length", 2.0f));
            boolean loop = booleanValue(clip, "loop", true);
            clips.put(name, new RuntimeAnimationClip(name, length, loop));
        }
        return Map.copyOf(clips);
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    private static String stringValue(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsString();
    }

    private static float floatValue(JsonObject object, String key, float defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsFloat();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsBoolean();
    }

    private static ModelRenderMode renderModeValue(JsonObject object, String key, ModelRenderMode defaultValue) {
        String value = stringValue(object, key, defaultValue.name());
        try {
            return ModelRenderMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }
}
