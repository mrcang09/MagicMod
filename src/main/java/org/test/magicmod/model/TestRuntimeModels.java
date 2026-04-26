package org.test.magicmod.model;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestRuntimeModels {
    private TestRuntimeModels() {
    }

    public static RuntimeModel createTestCube(Identifier id) {
        RuntimeMeshPart body = new RuntimeMeshPart("body", ModelRenderMode.DEBUG_SOLID, createCubeQuads());
        return new RuntimeModel(
            id,
            RuntimeSkeleton.EMPTY,
            List.of(body),
            Map.of(
                "idle", new RuntimeAnimationClip("idle", 2.0f, true),
                "spin", new RuntimeAnimationClip("spin", 1.5f, true),
                "pulse", new RuntimeAnimationClip("pulse", 1.0f, true)
            ),
            0.8f,
            1.8f
        );
    }

    private static List<RuntimeQuad> createCubeQuads() {
        List<RuntimeQuad> quads = new ArrayList<>();

        RuntimeVertex nwb = vertex(-0.5f, 0.0f, -0.5f, 54, 211, 153);
        RuntimeVertex neb = vertex(0.5f, 0.0f, -0.5f, 36, 191, 219);
        RuntimeVertex seb = vertex(0.5f, 0.0f, 0.5f, 96, 165, 250);
        RuntimeVertex swb = vertex(-0.5f, 0.0f, 0.5f, 251, 191, 36);
        RuntimeVertex nwt = vertex(-0.5f, 1.0f, -0.5f, 167, 139, 250);
        RuntimeVertex net = vertex(0.5f, 1.0f, -0.5f, 248, 113, 113);
        RuntimeVertex set = vertex(0.5f, 1.0f, 0.5f, 74, 222, 128);
        RuntimeVertex swt = vertex(-0.5f, 1.0f, 0.5f, 250, 204, 21);

        quads.add(new RuntimeQuad(nwb, neb, seb, swb));
        quads.add(new RuntimeQuad(nwt, swt, set, net));
        quads.add(new RuntimeQuad(nwb, nwt, net, neb));
        quads.add(new RuntimeQuad(neb, net, set, seb));
        quads.add(new RuntimeQuad(seb, set, swt, swb));
        quads.add(new RuntimeQuad(swb, swt, nwt, nwb));
        return quads;
    }

    private static RuntimeVertex vertex(float x, float y, float z, int red, int green, int blue) {
        return new RuntimeVertex(x, y, z, red, green, blue, 210);
    }
}
