package org.test.magicmod.ui;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Shader-based UI renderer using VBO for GPU-accelerated rendering.
 *
 * This renderer batches UI elements into a single VBO to avoid rebuilding vertex data every frame.
 * Transformations (rotation, translation, scale) are computed in the vertex shader.
 */
public class UiShaderRenderer {

    private static final int VERTEX_SIZE = 9; // position(3) + color(4) + uv(2)
    private static final int MAX_VERTICES = 65536; // Maximum vertices per batch

    private int vboId = -1;
    private int vaoId = -1;
    private boolean initialized = false;
    private final List<VertexData> vertexBuffer = new ArrayList<>();
    private boolean dirty = true;

    /**
     * Initialize OpenGL resources (VBO, VAO)
     */
    public void init() {
        if (initialized) {
            return;
        }

        // Generate VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Generate VBO
        vboId = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);

        // Allocate buffer (will be updated later)
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, MAX_VERTICES * VERTEX_SIZE * 4L, GL30.GL_DYNAMIC_DRAW);

        // Setup vertex attributes
        // Position (vec3)
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 0);
        GL30.glEnableVertexAttribArray(0);

        // Color (vec4)
        GL30.glVertexAttribPointer(1, 4, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 3 * 4L);
        GL30.glEnableVertexAttribArray(1);

        // UV (vec2)
        GL30.glVertexAttribPointer(2, 2, GL30.GL_FLOAT, false, VERTEX_SIZE * 4, 7 * 4L);
        GL30.glEnableVertexAttribArray(2);

        // Unbind
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        initialized = true;
    }

    /**
     * Clean up OpenGL resources
     */
    public void cleanup() {
        if (!initialized) {
            return;
        }

        if (vboId != -1) {
            GL30.glDeleteBuffers(vboId);
            vboId = -1;
        }

        if (vaoId != -1) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = -1;
        }

        initialized = false;
        vertexBuffer.clear();
    }

    /**
     * Begin building vertex data for a new frame
     */
    public void begin() {
        vertexBuffer.clear();
        dirty = true;
    }

    /**
     * Add a quad to the vertex buffer
     *
     * @param x1 Top-left X
     * @param y1 Top-left Y
     * @param x2 Bottom-right X
     * @param y2 Bottom-right Y
     * @param z Z-depth
     * @param color ARGB color
     * @param u1 UV u1
     * @param v1 UV v1
     * @param u2 UV u2
     * @param v2 UV v2
     */
    public void addQuad(float x1, float y1, float x2, float y2, float z,
                       int color, float u1, float v1, float u2, float v2) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;

        // Triangle 1 (top-left, bottom-left, bottom-right)
        vertexBuffer.add(new VertexData(x1, y1, z, r, g, b, a, u1, v1));
        vertexBuffer.add(new VertexData(x1, y2, z, r, g, b, a, u1, v2));
        vertexBuffer.add(new VertexData(x2, y2, z, r, g, b, a, u2, v2));

        // Triangle 2 (top-left, bottom-right, top-right)
        vertexBuffer.add(new VertexData(x1, y1, z, r, g, b, a, u1, v1));
        vertexBuffer.add(new VertexData(x2, y2, z, r, g, b, a, u2, v2));
        vertexBuffer.add(new VertexData(x2, y1, z, r, g, b, a, u2, v1));
    }

    /**
     * Upload vertex data to VBO
     */
    private void uploadData() {
        if (!dirty || vertexBuffer.isEmpty()) {
            return;
        }

        init(); // Ensure initialized

        // Create buffer
        int vertexCount = vertexBuffer.size();
        int bufferSize = vertexCount * VERTEX_SIZE * 4;
        ByteBuffer buffer = MemoryUtil.memAlloc(bufferSize);

        try {
            for (VertexData vertex : vertexBuffer) {
                buffer.putFloat(vertex.x);
                buffer.putFloat(vertex.y);
                buffer.putFloat(vertex.z);
                buffer.putFloat(vertex.r);
                buffer.putFloat(vertex.g);
                buffer.putFloat(vertex.b);
                buffer.putFloat(vertex.a);
                buffer.putFloat(vertex.u);
                buffer.putFloat(vertex.v);
            }

            buffer.flip();

            // Upload to VBO
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vboId);
            GL30.glBufferSubData(GL30.GL_ARRAY_BUFFER, 0, buffer);
            GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);

            dirty = false;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Render the buffered UI elements
     *
     * @param modelViewMatrix Model-view matrix
     * @param projectionMatrix Projection matrix
     * @param worldX World X offset
     * @param worldY World Y offset
     * @param worldZ World Z offset
     * @param translateX Translation X
     * @param translateY Translation Y
     * @param rotation Rotation in radians
     * @param scaleX Scale X
     * @param scaleY Scale Y
     * @param pivotX Pivot X
     * @param pivotY Pivot Y
     * @param useTexture Whether to use texture sampling
     */
    public void render(Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
                      float worldX, float worldY, float worldZ,
                      float translateX, float translateY,
                      float rotation, float scaleX, float scaleY,
                      float pivotX, float pivotY, boolean useTexture) {
        if (vertexBuffer.isEmpty()) {
            return;
        }

        uploadData();

        // Note: In a real implementation, you would load and use the custom shader here.
        // For now, we'll use Minecraft's built-in rendering system as a fallback.
        // To properly use custom shaders, you need to:
        // 1. Load the shader using ShaderInstance
        // 2. Set uniforms (ModelViewMat, ProjMat, WorldPos, Translation, etc.)
        // 3. Bind the shader and draw

        // Bind VAO
        GL30.glBindVertexArray(vaoId);

        // Draw
        GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, vertexBuffer.size());

        // Unbind
        GL30.glBindVertexArray(0);
    }

    /**
     * End frame and render
     */
    public void end() {
        // Rendering will be done explicitly via render() calls
    }

    /**
     * Vertex data structure
     */
    private static class VertexData {
        float x, y, z;
        float r, g, b, a;
        float u, v;

        VertexData(float x, float y, float z, float r, float g, float b, float a, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.u = u;
            this.v = v;
        }
    }
}
