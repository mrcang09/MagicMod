package org.test.magicmod.model.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.test.magicmod.model.ModelAssetSpec;
import org.test.magicmod.model.ModelRenderMode;
import org.test.magicmod.model.RuntimeAnimationKeyframe;
import org.test.magicmod.model.RuntimeBone;
import org.test.magicmod.model.RuntimeBoneAnimation;
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

public final class BedrockModelLoader {
    private static final float PIXELS_PER_BLOCK = 16.0f;

    private BedrockModelLoader() {
    }

    public static RuntimeModel load(ModelAssetSpec spec, ResourceManager resourceManager) {
        if (spec.geometry() == null || spec.texture() == null) {
            throw new IllegalArgumentException("Bedrock model spec requires geometry and texture resources");
        }

        try {
            JsonObject geometry = selectGeometry(readJson(resourceManager, spec.geometry()));
            JsonObject description = objectOrEmpty(geometry, "description");
            int textureWidth = intValue(description, "texture_width", 64);
            int textureHeight = intValue(description, "texture_height", 64);
            List<BoneDef> bones = parseBones(geometry);
            if (bones.size() > 128) {
                throw new IllegalArgumentException("Bedrock model exceeds first-version bone limit: " + bones.size());
            }

            List<RuntimeQuad> rawQuads = new ArrayList<>();
            for (int boneIndex = 0; boneIndex < bones.size(); boneIndex++) {
                BoneDef bone = bones.get(boneIndex);
                for (JsonObject cube : bone.cubes()) {
                    bakeCube(rawQuads, cube, boneIndex, textureWidth, textureHeight);
                }
            }
            if (rawQuads.isEmpty()) {
                throw new IllegalArgumentException("Bedrock geometry has no bakeable cubes: " + spec.geometry());
            }

            BakedBounds bounds = BakedBounds.from(rawQuads);
            List<RuntimeQuad> quads = normalizeQuads(rawQuads, bounds);
            RuntimeSkeleton skeleton = createSkeleton(bones, bounds);
            Map<String, RuntimeAnimationClip> animations = loadAnimations(spec, resourceManager, bounds);
            RuntimeMeshPart meshPart = new RuntimeMeshPart(
                "bedrock",
                normalizeRenderMode(spec.renderMode()),
                spec.texture(),
                quads
            );
            return new RuntimeModel(
                spec.modelId(),
                skeleton,
                List.of(meshPart),
                animations,
                Math.max(0.25f, bounds.horizontalSize()),
                Math.max(0.25f, bounds.height())
            );
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed to load Bedrock model resource: " + spec.displayName(), error);
        }
    }

    private static JsonObject readJson(ResourceManager resourceManager, Identifier id) throws IOException {
        try (BufferedReader reader = resourceManager.openAsReader(id)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject selectGeometry(JsonObject root) {
        JsonArray geometries = array(root, "minecraft:geometry");
        if (geometries != null && !geometries.isEmpty()) {
            return geometries.get(0).getAsJsonObject();
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
                return entry.getValue().getAsJsonObject();
            }
        }
        throw new IllegalArgumentException("Bedrock geometry JSON must contain minecraft:geometry");
    }

    private static List<BoneDef> parseBones(JsonObject geometry) {
        JsonArray boneArray = array(geometry, "bones");
        if (boneArray == null || boneArray.isEmpty()) {
            throw new IllegalArgumentException("Bedrock geometry must contain at least one bone");
        }
        List<BoneDef> bones = new ArrayList<>();
        for (JsonElement element : boneArray) {
            JsonObject bone = element.getAsJsonObject();
            String name = stringValue(bone, "name", "bone_" + bones.size());
            String parent = stringValue(bone, "parent", "");
            float[] pivot = floatArray(bone, "pivot", 3, 0.0f);
            JsonArray cubeArray = array(bone, "cubes");
            List<JsonObject> cubes = new ArrayList<>();
            if (cubeArray != null) {
                for (JsonElement cube : cubeArray) {
                    if (cube.isJsonObject()) {
                        cubes.add(cube.getAsJsonObject());
                    }
                }
            }
            bones.add(new BoneDef(name, parent, pivot, cubes));
        }
        return bones;
    }

    private static void bakeCube(
        List<RuntimeQuad> quads,
        JsonObject cube,
        int boneIndex,
        int textureWidth,
        int textureHeight
    ) {
        float[] origin = floatArray(cube, "origin", 3, 0.0f);
        float[] size = floatArray(cube, "size", 3, 0.0f);
        if (size[0] <= 0.0f || size[1] <= 0.0f || size[2] <= 0.0f) {
            return;
        }
        float inflate = floatValue(cube, "inflate", 0.0f);

        float minX = (origin[0] - inflate) / PIXELS_PER_BLOCK;
        float minY = (origin[1] - inflate) / PIXELS_PER_BLOCK;
        float minZ = (origin[2] - inflate) / PIXELS_PER_BLOCK;
        float maxX = (origin[0] + size[0] + inflate) / PIXELS_PER_BLOCK;
        float maxY = (origin[1] + size[1] + inflate) / PIXELS_PER_BLOCK;
        float maxZ = (origin[2] + size[2] + inflate) / PIXELS_PER_BLOCK;

        UvResolver uv = UvResolver.create(cube.get("uv"), size, textureWidth, textureHeight);
        quads.add(face(
            vertex(minX, minY, minZ, uv.rect("north", size[0], size[1]).u0(), uv.rect("north", size[0], size[1]).v1(), 0.0f, 0.0f, -1.0f, boneIndex),
            vertex(maxX, minY, minZ, uv.rect("north", size[0], size[1]).u1(), uv.rect("north", size[0], size[1]).v1(), 0.0f, 0.0f, -1.0f, boneIndex),
            vertex(maxX, maxY, minZ, uv.rect("north", size[0], size[1]).u1(), uv.rect("north", size[0], size[1]).v0(), 0.0f, 0.0f, -1.0f, boneIndex),
            vertex(minX, maxY, minZ, uv.rect("north", size[0], size[1]).u0(), uv.rect("north", size[0], size[1]).v0(), 0.0f, 0.0f, -1.0f, boneIndex)
        ));
        quads.add(face(
            vertex(maxX, minY, maxZ, uv.rect("south", size[0], size[1]).u0(), uv.rect("south", size[0], size[1]).v1(), 0.0f, 0.0f, 1.0f, boneIndex),
            vertex(minX, minY, maxZ, uv.rect("south", size[0], size[1]).u1(), uv.rect("south", size[0], size[1]).v1(), 0.0f, 0.0f, 1.0f, boneIndex),
            vertex(minX, maxY, maxZ, uv.rect("south", size[0], size[1]).u1(), uv.rect("south", size[0], size[1]).v0(), 0.0f, 0.0f, 1.0f, boneIndex),
            vertex(maxX, maxY, maxZ, uv.rect("south", size[0], size[1]).u0(), uv.rect("south", size[0], size[1]).v0(), 0.0f, 0.0f, 1.0f, boneIndex)
        ));
        quads.add(face(
            vertex(minX, minY, maxZ, uv.rect("west", size[2], size[1]).u0(), uv.rect("west", size[2], size[1]).v1(), -1.0f, 0.0f, 0.0f, boneIndex),
            vertex(minX, minY, minZ, uv.rect("west", size[2], size[1]).u1(), uv.rect("west", size[2], size[1]).v1(), -1.0f, 0.0f, 0.0f, boneIndex),
            vertex(minX, maxY, minZ, uv.rect("west", size[2], size[1]).u1(), uv.rect("west", size[2], size[1]).v0(), -1.0f, 0.0f, 0.0f, boneIndex),
            vertex(minX, maxY, maxZ, uv.rect("west", size[2], size[1]).u0(), uv.rect("west", size[2], size[1]).v0(), -1.0f, 0.0f, 0.0f, boneIndex)
        ));
        quads.add(face(
            vertex(maxX, minY, minZ, uv.rect("east", size[2], size[1]).u0(), uv.rect("east", size[2], size[1]).v1(), 1.0f, 0.0f, 0.0f, boneIndex),
            vertex(maxX, minY, maxZ, uv.rect("east", size[2], size[1]).u1(), uv.rect("east", size[2], size[1]).v1(), 1.0f, 0.0f, 0.0f, boneIndex),
            vertex(maxX, maxY, maxZ, uv.rect("east", size[2], size[1]).u1(), uv.rect("east", size[2], size[1]).v0(), 1.0f, 0.0f, 0.0f, boneIndex),
            vertex(maxX, maxY, minZ, uv.rect("east", size[2], size[1]).u0(), uv.rect("east", size[2], size[1]).v0(), 1.0f, 0.0f, 0.0f, boneIndex)
        ));
        quads.add(face(
            vertex(minX, maxY, minZ, uv.rect("up", size[0], size[2]).u0(), uv.rect("up", size[0], size[2]).v1(), 0.0f, 1.0f, 0.0f, boneIndex),
            vertex(maxX, maxY, minZ, uv.rect("up", size[0], size[2]).u1(), uv.rect("up", size[0], size[2]).v1(), 0.0f, 1.0f, 0.0f, boneIndex),
            vertex(maxX, maxY, maxZ, uv.rect("up", size[0], size[2]).u1(), uv.rect("up", size[0], size[2]).v0(), 0.0f, 1.0f, 0.0f, boneIndex),
            vertex(minX, maxY, maxZ, uv.rect("up", size[0], size[2]).u0(), uv.rect("up", size[0], size[2]).v0(), 0.0f, 1.0f, 0.0f, boneIndex)
        ));
        quads.add(face(
            vertex(minX, minY, maxZ, uv.rect("down", size[0], size[2]).u0(), uv.rect("down", size[0], size[2]).v1(), 0.0f, -1.0f, 0.0f, boneIndex),
            vertex(maxX, minY, maxZ, uv.rect("down", size[0], size[2]).u1(), uv.rect("down", size[0], size[2]).v1(), 0.0f, -1.0f, 0.0f, boneIndex),
            vertex(maxX, minY, minZ, uv.rect("down", size[0], size[2]).u1(), uv.rect("down", size[0], size[2]).v0(), 0.0f, -1.0f, 0.0f, boneIndex),
            vertex(minX, minY, minZ, uv.rect("down", size[0], size[2]).u0(), uv.rect("down", size[0], size[2]).v0(), 0.0f, -1.0f, 0.0f, boneIndex)
        ));
    }

    private static RuntimeQuad face(RuntimeVertex a, RuntimeVertex b, RuntimeVertex c, RuntimeVertex d) {
        return new RuntimeQuad(a, b, c, d);
    }

    private static RuntimeVertex vertex(
        float x,
        float y,
        float z,
        float u,
        float v,
        float normalX,
        float normalY,
        float normalZ,
        int boneIndex
    ) {
        return new RuntimeVertex(x, y, z, u, v, normalX, normalY, normalZ, boneIndex, 255, 255, 255, 255);
    }

    private static List<RuntimeQuad> normalizeQuads(List<RuntimeQuad> quads, BakedBounds bounds) {
        float centerX = (bounds.minX() + bounds.maxX()) * 0.5f;
        float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5f;
        float horizontal = Math.max(0.001f, bounds.horizontalSize());
        float vertical = Math.max(0.001f, bounds.height());
        List<RuntimeQuad> normalized = new ArrayList<>(quads.size());
        for (RuntimeQuad quad : quads) {
            normalized.add(new RuntimeQuad(
                normalizeVertex(quad.a(), centerX, centerZ, bounds.minY(), horizontal, vertical),
                normalizeVertex(quad.b(), centerX, centerZ, bounds.minY(), horizontal, vertical),
                normalizeVertex(quad.c(), centerX, centerZ, bounds.minY(), horizontal, vertical),
                normalizeVertex(quad.d(), centerX, centerZ, bounds.minY(), horizontal, vertical)
            ));
        }
        return normalized;
    }

    private static RuntimeVertex normalizeVertex(
        RuntimeVertex vertex,
        float centerX,
        float centerZ,
        float minY,
        float horizontal,
        float vertical
    ) {
        return vertex.withPosition(
            normalizeX(vertex.x(), centerX, horizontal),
            normalizeY(vertex.y(), minY, vertical),
            normalizeZ(vertex.z(), centerZ, horizontal)
        );
    }

    private static float normalizeX(float value, BakedBounds bounds) {
        return normalizeX(value, (bounds.minX() + bounds.maxX()) * 0.5f, Math.max(0.001f, bounds.horizontalSize()));
    }

    private static float normalizeY(float value, BakedBounds bounds) {
        return normalizeY(value, bounds.minY(), Math.max(0.001f, bounds.height()));
    }

    private static float normalizeZ(float value, BakedBounds bounds) {
        return normalizeZ(value, (bounds.minZ() + bounds.maxZ()) * 0.5f, Math.max(0.001f, bounds.horizontalSize()));
    }

    private static float normalizeX(float value, float centerX, float horizontal) {
        return (value - centerX) / horizontal;
    }

    private static float normalizeY(float value, float minY, float vertical) {
        return (value - minY) / vertical;
    }

    private static float normalizeZ(float value, float centerZ, float horizontal) {
        return (value - centerZ) / horizontal;
    }

    private static RuntimeSkeleton createSkeleton(List<BoneDef> bones, BakedBounds bounds) {
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int index = 0; index < bones.size(); index++) {
            indices.put(bones.get(index).name(), index);
        }

        List<RuntimeBone> runtimeBones = new ArrayList<>(bones.size());
        for (BoneDef bone : bones) {
            Integer parentIndex = bone.parentName().isBlank() ? null : indices.get(bone.parentName());
            float[] pivot = bone.pivot();
            runtimeBones.add(new RuntimeBone(
                bone.name(),
                parentIndex == null ? -1 : parentIndex,
                normalizeX(pivot[0] / PIXELS_PER_BLOCK, bounds),
                normalizeY(pivot[1] / PIXELS_PER_BLOCK, bounds),
                normalizeZ(pivot[2] / PIXELS_PER_BLOCK, bounds)
            ));
        }
        return new RuntimeSkeleton(runtimeBones);
    }

    private static Map<String, RuntimeAnimationClip> loadAnimations(
        ModelAssetSpec spec,
        ResourceManager resourceManager,
        BakedBounds bounds
    )
        throws IOException {
        Map<String, RuntimeAnimationClip> clips = new LinkedHashMap<>();
        if (spec.animation() != null) {
            JsonObject root = readJson(resourceManager, spec.animation());
            JsonObject animations = objectOrEmpty(root, "animations");
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject clip = entry.getValue().getAsJsonObject();
                float length = floatValue(clip, "animation_length", estimateAnimationLength(clip));
                boolean loop = loopValue(clip.get("loop"));
                clips.put(entry.getKey(), new RuntimeAnimationClip(
                    entry.getKey(),
                    Math.max(0.05f, length),
                    loop,
                    parseBoneAnimations(clip, bounds)
                ));
            }
        }
        if (clips.isEmpty()) {
            clips.put("idle", new RuntimeAnimationClip("idle", 2.0f, true));
        }
        return clips;
    }

    private static Map<String, RuntimeBoneAnimation> parseBoneAnimations(JsonObject clip, BakedBounds bounds) {
        JsonObject bones = objectOrEmpty(clip, "bones");
        Map<String, RuntimeBoneAnimation> animations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject bone = entry.getValue().getAsJsonObject();
            RuntimeBoneAnimation animation = new RuntimeBoneAnimation(
                parseChannel(bone.get("position"), ChannelUnit.POSITION, bounds),
                parseChannel(bone.get("rotation"), ChannelUnit.ROTATION, bounds),
                parseChannel(bone.get("scale"), ChannelUnit.SCALE, bounds)
            );
            if (!animation.isEmpty()) {
                animations.put(entry.getKey(), animation);
            }
        }
        return animations;
    }

    private static List<RuntimeAnimationKeyframe> parseChannel(JsonElement element, ChannelUnit unit, BakedBounds bounds) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonArray()) {
            return List.of(toKeyframe(0.0f, element, unit, bounds));
        }
        if (!element.isJsonObject()) {
            return List.of();
        }

        List<RuntimeAnimationKeyframe> frames = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            float time = parseFloat(entry.getKey(), 0.0f);
            frames.add(toKeyframe(time, unwrapKeyframeValue(entry.getValue()), unit, bounds));
        }
        return frames;
    }

    private static JsonElement unwrapKeyframeValue(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement post = object.get("post");
            if (post != null) {
                return post;
            }
            JsonElement vector = object.get("vector");
            if (vector != null) {
                return vector;
            }
        }
        return element;
    }

    private static RuntimeAnimationKeyframe toKeyframe(float time, JsonElement element, ChannelUnit unit, BakedBounds bounds) {
        float[] values = jsonVector(element, unit == ChannelUnit.SCALE ? 1.0f : 0.0f);
        if (unit == ChannelUnit.ROTATION) {
            return new RuntimeAnimationKeyframe(
                time,
                (float) Math.toRadians(values[0]),
                (float) Math.toRadians(values[1]),
                (float) Math.toRadians(values[2])
            );
        }
        if (unit == ChannelUnit.POSITION) {
            return new RuntimeAnimationKeyframe(
                time,
                values[0] / PIXELS_PER_BLOCK / Math.max(0.001f, bounds.horizontalSize()),
                values[1] / PIXELS_PER_BLOCK / Math.max(0.001f, bounds.height()),
                values[2] / PIXELS_PER_BLOCK / Math.max(0.001f, bounds.horizontalSize())
            );
        }
        return new RuntimeAnimationKeyframe(time, values[0], values[1], values[2]);
    }

    private static float[] jsonVector(JsonElement element, float defaultValue) {
        float[] values = {defaultValue, defaultValue, defaultValue};
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < Math.min(3, array.size()); index++) {
                values[index] = array.get(index).getAsFloat();
            }
            return values;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            values[0] = element.getAsFloat();
            values[1] = element.getAsFloat();
            values[2] = element.getAsFloat();
        }
        return values;
    }

    private static float estimateAnimationLength(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 1.0f;
        }
        if (element.isJsonObject()) {
            float max = 0.0f;
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                max = Math.max(max, parseFloat(entry.getKey(), 0.0f));
                max = Math.max(max, estimateAnimationLength(entry.getValue()));
            }
            return max <= 0.0f ? 1.0f : max;
        }
        if (element.isJsonArray()) {
            float max = 0.0f;
            for (JsonElement child : element.getAsJsonArray()) {
                max = Math.max(max, estimateAnimationLength(child));
            }
            return max <= 0.0f ? 1.0f : max;
        }
        return 1.0f;
    }

    private static boolean loopValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return true;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return "loop".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        }
        return true;
    }

    private static ModelRenderMode normalizeRenderMode(ModelRenderMode mode) {
        if (mode == ModelRenderMode.DEBUG_SOLID) {
            return ModelRenderMode.CUTOUT;
        }
        return mode;
    }

    private static JsonObject objectOrEmpty(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return null;
    }

    private static String stringValue(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsString();
    }

    private static int intValue(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsInt();
    }

    private static float floatValue(JsonObject object, String key, float defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return element.getAsFloat();
    }

    private static float[] floatArray(JsonObject object, String key, int length, float defaultValue) {
        float[] values = new float[length];
        for (int index = 0; index < length; index++) {
            values[index] = defaultValue;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return values;
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < Math.min(length, array.size()); index++) {
            values[index] = array.get(index).getAsFloat();
        }
        return values;
    }

    private static float parseFloat(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private enum ChannelUnit {
        POSITION,
        ROTATION,
        SCALE
    }

    private record BoneDef(String name, String parentName, float[] pivot, List<JsonObject> cubes) {
        private BoneDef {
            if (parentName == null) {
                parentName = "";
            }
            if (pivot == null || pivot.length < 3) {
                pivot = new float[] {0.0f, 0.0f, 0.0f};
            }
        }
    }

    private record UvRect(float u0, float v0, float u1, float v1) {
    }

    private static final class UvResolver {
        private final JsonElement uv;
        private final float textureWidth;
        private final float textureHeight;

        private UvResolver(JsonElement uv, float textureWidth, float textureHeight) {
            this.uv = uv;
            this.textureWidth = Math.max(1.0f, textureWidth);
            this.textureHeight = Math.max(1.0f, textureHeight);
        }

        static UvResolver create(JsonElement uv, float[] size, int textureWidth, int textureHeight) {
            return new UvResolver(uv, textureWidth, textureHeight);
        }

        UvRect rect(String face, float width, float height) {
            if (uv != null && uv.isJsonObject()) {
                JsonElement faceElement = uv.getAsJsonObject().get(face);
                if (faceElement != null && faceElement.isJsonObject()) {
                    JsonObject faceObject = faceElement.getAsJsonObject();
                    float[] pos = floatArray(faceObject, "uv", 2, 0.0f);
                    float[] size = floatArray(faceObject, "uv_size", 2, 0.0f);
                    if (size[0] == 0.0f) {
                        size[0] = width;
                    }
                    if (size[1] == 0.0f) {
                        size[1] = height;
                    }
                    return toRect(pos[0], pos[1], pos[0] + size[0], pos[1] + size[1]);
                }
            }
            if (uv != null && uv.isJsonArray()) {
                JsonArray array = uv.getAsJsonArray();
                float u = array.size() > 0 ? array.get(0).getAsFloat() : 0.0f;
                float v = array.size() > 1 ? array.get(1).getAsFloat() : 0.0f;
                return toRect(u, v, u + width, v + height);
            }
            return toRect(0.0f, 0.0f, width, height);
        }

        private UvRect toRect(float u0, float v0, float u1, float v1) {
            return new UvRect(u0 / textureWidth, v0 / textureHeight, u1 / textureWidth, v1 / textureHeight);
        }
    }

    private record BakedBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static BakedBounds from(List<RuntimeQuad> quads) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (RuntimeQuad quad : quads) {
                RuntimeVertex[] vertices = {quad.a(), quad.b(), quad.c(), quad.d()};
                for (RuntimeVertex vertex : vertices) {
                    minX = Math.min(minX, vertex.x());
                    minY = Math.min(minY, vertex.y());
                    minZ = Math.min(minZ, vertex.z());
                    maxX = Math.max(maxX, vertex.x());
                    maxY = Math.max(maxY, vertex.y());
                    maxZ = Math.max(maxZ, vertex.z());
                }
            }
            return new BakedBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        float height() {
            return Math.max(0.001f, maxY - minY);
        }

        float horizontalSize() {
            return Math.max(Math.max(maxX - minX, maxZ - minZ), 0.001f);
        }
    }
}
