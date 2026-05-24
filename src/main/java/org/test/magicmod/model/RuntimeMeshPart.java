package org.test.magicmod.model;

import net.minecraft.resources.Identifier;

import java.util.List;

public final class RuntimeMeshPart implements AutoCloseable {
    private final String name;
    private final ModelRenderMode renderMode;
    private final Identifier texture;
    private final List<RuntimeQuad> quads;
    private final GpuMeshBuffer gpuBuffer;

    public RuntimeMeshPart(String name, ModelRenderMode renderMode, List<RuntimeQuad> quads) {
        this(name, renderMode, null, quads);
    }

    public RuntimeMeshPart(String name, ModelRenderMode renderMode, Identifier texture, List<RuntimeQuad> quads) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Mesh part name cannot be blank");
        }
        if (renderMode == null) {
            throw new IllegalArgumentException("Render mode cannot be null");
        }
        if (quads == null || quads.isEmpty()) {
            throw new IllegalArgumentException("Mesh part must contain at least one quad");
        }
        this.name = name;
        this.renderMode = renderMode;
        this.texture = texture;
        this.quads = List.copyOf(quads);
        this.gpuBuffer = new GpuMeshBuffer();
    }

    public String name() {
        return name;
    }

    public ModelRenderMode renderMode() {
        return renderMode;
    }

    public Identifier texture() {
        return texture;
    }

    public List<RuntimeQuad> quads() {
        return quads;
    }

    @Override
    public void close() {
        gpuBuffer.close();
    }
}
