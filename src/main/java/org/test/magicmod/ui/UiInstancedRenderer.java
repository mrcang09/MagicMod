package org.test.magicmod.ui;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * GPU instanced renderer for rendering multiple UI quads with a single draw call.
 * This significantly reduces draw calls when using texture atlas.
 */
public class UiInstancedRenderer {

    private static final int VERTEX_SIZE = 5; // position(2) + uv(2) + texIndex(1)
    private static final int INSTANCE_SIZE = 13; // position(2) + size(2) + uv(4) + color(4) + alpha(1)
    private static final int MAX_INSTANCES = 65536;

    private int vboId = -1;
    private int vaoId = -1;
    private int instanceVboId = -1;
    private boolean initialized = false;
    private final List<InstanceData> instances = new ArrayList<>();
    private boolean dirty = true;

    /**
     * Initialize OpenGL resources
     */
    public void init() {
        if (initialized) {
            return;
        }

        try {
        // Generate VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create quad VBO (shared geometry for all instances)
        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

        // Quad vertices: position(2) + uv(2) + texIndex(1)
        float[] quadVertices = {
            // Triangle 1
            0.0f, 0.0f,  0.0f, 0.0f, 0.0f,  // Top-left
            0.0f, 1.0f,  0.0f, 1.0f, 0.0f,  // Bottom-left
            1.0f, 1.0f,  1.0f, 1.0f, 0.0f,  // Bottom-right
            // Triangle 2
            0.0f, 0.0f,  0.0f, 0.0f, 0.0f,  // Top-left
            1.0f, 1.0f,  1.0f, 1.0f, 0.0f,  // Bottom-right
            1.0f, 0.0f,  1.0f, 0.0f, 0.0f   // Top-right
        };

        ByteBuffer buffer = MemoryUtil.memAlloc(quadVertices.length * Float.BYTES);
        try {
            for (float v : quadVertices) {
                buffer.putFloat(v);
            }
            buffer.flip();
            GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buffer, GL30.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(buffer);
        }

        // Position attribute (location 0)
        GL30.glVertexAttribPointer(0, 2, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 0);
        GL30.glEnableVertexAttribArray(0);

        // Base UV attribute (location 1)
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 2 * 4L);
        GL30.glEnableVertexAttribArray(1);

        // Texture index (location 2) - not used currently
        GL30.glVertexAttribPointer(2, 1, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 4 * 4L);
        GL30.glEnableVertexAttribArray(2);

        // Create instance VBO
        instanceVboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, instanceVboId);
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, MAX_INSTANCES * INSTANCE_SIZE * (long) Float.BYTES, GL30.GL_DYNAMIC_DRAW);

        // Instance attributes (position, size, UV offset/scale, color)
        // Instance position (location 3)
        GL30.glVertexAttribPointer(3, 2, GL30.GL_FLOAT, false, 13 * 4, 0);
        GL30.glEnableVertexAttribArray(3);
        GL33.glVertexAttribDivisor(3, 1);

        // Instance size (location 4)
        GL30.glVertexAttribPointer(4, 2, GL30.GL_FLOAT, false, 13 * 4, 2 * 4L);
        GL30.glEnableVertexAttribArray(4);
        GL33.glVertexAttribDivisor(4, 1);

        // Instance UV offset (location 5)
        GL30.glVertexAttribPointer(5, 2, GL30.GL_FLOAT, false, 13 * 4, 4 * 4L);
        GL30.glEnableVertexAttribArray(5);
        GL33.glVertexAttribDivisor(5, 1);

        // Instance UV scale (location 6)
        GL30.glVertexAttribPointer(6, 2, GL30.GL_FLOAT, false, 13 * 4, 6 * 4L);
        GL30.glEnableVertexAttribArray(6);
        GL33.glVertexAttribDivisor(6, 1);

        // Instance color (location 7)
        GL30.glVertexAttribPointer(7, 4, GL30.GL_FLOAT, false, 13 * 4, 8 * 4L);
        GL30.glEnableVertexAttribArray(7);
        GL33.glVertexAttribDivisor(7, 1);

        // Instance alpha (location 8)
        GL30.glVertexAttribPointer(8, 1, GL30.GL_FLOAT, false, 13 * 4, 12 * 4L);
        GL30.glEnableVertexAttribArray(8);
        GL33.glVertexAttribDivisor(8, 1);

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        initialized = true;
        } catch (RuntimeException | Error error) {
            cleanup();
            throw error;
        }
    }

    /**
     * Begin a new batch
     */
    public void begin() {
        instances.clear();
        dirty = true;
    }

    /**
     * Add an instance to render
     */
    public void addInstance(float x, float y, float width, float height,
                           float u, float v, float u2, float v2,
                           int color, float alpha) {
        if (instances.size() >= MAX_INSTANCES) {
            return;
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        instances.add(new InstanceData(x, y, width, height, u, v, u2 - u, v2 - v, r, g, b, a, alpha));
        dirty = true;
    }

    /**
     * Upload instance data to GPU
     */
    private void uploadInstances() {
        if (!dirty || instances.isEmpty()) {
            return;
        }

        init(); // Ensure initialized

        int bufferSize = instances.size() * INSTANCE_SIZE * Float.BYTES;
        ByteBuffer buffer = MemoryUtil.memAlloc(bufferSize);

        try {
            for (InstanceData instance : instances) {
                buffer.putFloat(instance.x);
                buffer.putFloat(instance.y);
                buffer.putFloat(instance.width);
                buffer.putFloat(instance.height);
                buffer.putFloat(instance.uOffset);
                buffer.putFloat(instance.vOffset);
                buffer.putFloat(instance.uScale);
                buffer.putFloat(instance.vScale);
                buffer.putFloat(instance.r);
                buffer.putFloat(instance.g);
                buffer.putFloat(instance.b);
                buffer.putFloat(instance.a);
                buffer.putFloat(instance.alpha);
            }

            buffer.flip();

            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, instanceVboId);
            GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, buffer);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

            dirty = false;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Render all instances
     */
    public void render() {
        if (instances.isEmpty()) {
            return;
        }

        uploadInstances();

        GL30.glBindVertexArray(vaoId);
        GL33.glDrawArraysInstanced(GL30.GL_TRIANGLES, 0, 6, instances.size());
        GL30.glBindVertexArray(0);
    }

    /**
     * Get number of instances in current batch
     */
    public int getInstanceCount() {
        return instances.size();
    }

    /**
     * Cleanup OpenGL resources
     */
    public void cleanup() {
        if (vboId != -1) {
            GL30.glDeleteBuffers(vboId);
            vboId = -1;
        }

        if (instanceVboId != -1) {
            GL30.glDeleteBuffers(instanceVboId);
            instanceVboId = -1;
        }

        if (vaoId != -1) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = -1;
        }

        initialized = false;
        instances.clear();
        dirty = true;
    }

    /**
     * Instance data
     */
    private static class InstanceData {
        float x, y, width, height;
        float uOffset, vOffset, uScale, vScale;
        float r, g, b, a;
        float alpha;

        InstanceData(float x, float y, float width, float height,
                    float uOffset, float vOffset, float uScale, float vScale,
                    float r, float g, float b, float a, float alpha) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.uOffset = uOffset;
            this.vOffset = vOffset;
            this.uScale = uScale;
            this.vScale = vScale;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.alpha = alpha;
        }
    }
}
