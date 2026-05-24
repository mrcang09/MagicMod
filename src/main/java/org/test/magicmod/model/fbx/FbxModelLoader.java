package org.test.magicmod.model.fbx;

import net.minecraft.server.packs.resources.ResourceManager;
import org.test.magicmod.model.ModelAssetSpec;
import org.test.magicmod.model.ModelRenderMode;
import org.test.magicmod.model.RuntimeAnimationClip;
import org.test.magicmod.model.RuntimeMeshPart;
import org.test.magicmod.model.RuntimeModel;
import org.test.magicmod.model.RuntimeQuad;
import org.test.magicmod.model.RuntimeSkeleton;
import org.test.magicmod.model.RuntimeVertex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FbxModelLoader {
    private FbxModelLoader() {
    }

    public static RuntimeModel load(ModelAssetSpec spec, ResourceManager resourceManager) {
        if (spec.geometry() == null || spec.texture() == null) {
            throw new IllegalArgumentException("FBX model spec requires source and texture resources");
        }

        try {
            String text;
            try (var reader = resourceManager.openAsReader(spec.geometry())) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                text = builder.toString();
            }

            List<Vec3> vertices = parseVertices(text);
            List<int[]> polygons = parsePolygons(text);
            if (vertices.isEmpty() || polygons.isEmpty()) {
                throw new IllegalArgumentException("ASCII FBX mesh must contain Vertices and PolygonVertexIndex arrays");
            }

            Bounds bounds = Bounds.from(vertices);
            List<RuntimeQuad> quads = new ArrayList<>();
            for (int[] polygon : polygons) {
                if (polygon.length < 3) {
                    continue;
                }
                for (int index = 1; index < polygon.length - 1; index++) {
                    quads.add(toQuad(vertices, bounds, polygon[0], polygon[index], polygon[index + 1]));
                }
            }
            if (quads.isEmpty()) {
                throw new IllegalArgumentException("ASCII FBX mesh has no renderable polygons");
            }

            RuntimeMeshPart meshPart = new RuntimeMeshPart(
                "fbx",
                normalizeRenderMode(spec.renderMode()),
                spec.texture(),
                quads
            );
            return new RuntimeModel(
                spec.modelId(),
                RuntimeSkeleton.EMPTY,
                List.of(meshPart),
                Map.of(
                    "idle", new RuntimeAnimationClip("idle", 2.0f, true),
                    "spin", new RuntimeAnimationClip("spin", 1.5f, true),
                    "pulse", new RuntimeAnimationClip("pulse", 1.0f, true)
                ),
                Math.max(0.25f, bounds.horizontalSize()),
                Math.max(0.25f, bounds.height())
            );
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed to load FBX model resource: " + spec.displayName(), error);
        }
    }

    private static List<Vec3> parseVertices(String text) {
        List<Float> values = parseFloatBlock(text, "Vertices:");
        List<Vec3> vertices = new ArrayList<>();
        for (int index = 0; index + 2 < values.size(); index += 3) {
            vertices.add(new Vec3(values.get(index), values.get(index + 1), values.get(index + 2)));
        }
        return vertices;
    }

    private static List<int[]> parsePolygons(String text) {
        List<Integer> values = parseIntBlock(text, "PolygonVertexIndex:");
        List<int[]> polygons = new ArrayList<>();
        List<Integer> polygon = new ArrayList<>();
        for (int raw : values) {
            if (raw < 0) {
                polygon.add(-raw - 1);
                polygons.add(polygon.stream().mapToInt(Integer::intValue).toArray());
                polygon.clear();
            } else {
                polygon.add(raw);
            }
        }
        if (!polygon.isEmpty()) {
            polygons.add(polygon.stream().mapToInt(Integer::intValue).toArray());
        }
        return polygons;
    }

    private static List<Float> parseFloatBlock(String text, String key) {
        String block = arrayBlock(text, key);
        if (block.isBlank()) {
            return List.of();
        }
        List<Float> values = new ArrayList<>();
        for (String token : block.split("[,\\s]+")) {
            if (!token.isBlank()) {
                values.add(Float.parseFloat(token));
            }
        }
        return values;
    }

    private static List<Integer> parseIntBlock(String text, String key) {
        String block = arrayBlock(text, key);
        if (block.isBlank()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (String token : block.split("[,\\s]+")) {
            if (!token.isBlank()) {
                values.add(Integer.parseInt(token));
            }
        }
        return values;
    }

    private static String arrayBlock(String text, String key) {
        int keyIndex = text.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }
        int arrayIndex = text.indexOf("a:", keyIndex);
        if (arrayIndex < 0) {
            return "";
        }
        int start = arrayIndex + 2;
        int end = text.indexOf('}', start);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end).trim();
    }

    private static RuntimeQuad toQuad(List<Vec3> vertices, Bounds bounds, int first, int second, int third) {
        Vec3 a = vertices.get(first);
        Vec3 b = vertices.get(second);
        Vec3 c = vertices.get(third);
        Vec3 normal = normal(a, b, c);
        RuntimeVertex va = vertex(a, bounds, normal, 0.0f, 1.0f);
        RuntimeVertex vb = vertex(b, bounds, normal, 1.0f, 1.0f);
        RuntimeVertex vc = vertex(c, bounds, normal, 1.0f, 0.0f);
        return new RuntimeQuad(va, vb, vc, vc);
    }

    private static RuntimeVertex vertex(Vec3 vertex, Bounds bounds, Vec3 normal, float u, float v) {
        return new RuntimeVertex(
            (vertex.x() - bounds.centerX()) / bounds.horizontalSize(),
            (vertex.y() - bounds.minY()) / bounds.height(),
            (vertex.z() - bounds.centerZ()) / bounds.horizontalSize(),
            u,
            v,
            normal.x(),
            normal.y(),
            normal.z(),
            0,
            255,
            255,
            255,
            255
        );
    }

    private static Vec3 normal(Vec3 a, Vec3 b, Vec3 c) {
        float abX = b.x() - a.x();
        float abY = b.y() - a.y();
        float abZ = b.z() - a.z();
        float acX = c.x() - a.x();
        float acY = c.y() - a.y();
        float acZ = c.z() - a.z();
        float x = abY * acZ - abZ * acY;
        float y = abZ * acX - abX * acZ;
        float z = abX * acY - abY * acX;
        float length = Math.max(0.0001f, (float) Math.sqrt(x * x + y * y + z * z));
        return new Vec3(x / length, y / length, z / length);
    }

    private static ModelRenderMode normalizeRenderMode(ModelRenderMode mode) {
        if (mode == ModelRenderMode.DEBUG_SOLID) {
            return ModelRenderMode.CUTOUT;
        }
        return mode;
    }

    private record Vec3(float x, float y, float z) {
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds from(List<Vec3> vertices) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (Vec3 vertex : vertices) {
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        float centerX() {
            return (minX + maxX) * 0.5f;
        }

        float centerZ() {
            return (minZ + maxZ) * 0.5f;
        }

        float height() {
            return Math.max(0.001f, maxY - minY);
        }

        float horizontalSize() {
            return Math.max(Math.max(maxX - minX, maxZ - minZ), 0.001f);
        }
    }
}
