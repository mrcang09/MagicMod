#version 150

// 从顶点着色器输入
in vec4 vertexColor;
in vec2 texCoord;

// 输出颜色
out vec4 fragColor;

// Uniform变量
uniform sampler2D Sampler0;   // 纹理采样器
uniform int UseTexture;       // 是否使用纹理（0=否，1=是）

void main() {
    vec4 color;

    if (UseTexture == 1) {
        // 采样纹理并乘以顶点颜色
        vec4 texColor = texture(Sampler0, texCoord);
        color = texColor * vertexColor;
    } else {
        // 不使用纹理，直接使用顶点颜色
        color = vertexColor;
    }

    // 输出最终颜色
    fragColor = color;

    // Alpha测试：完全透明的像素丢弃
    if (fragColor.a < 0.01) {
        discard;
    }
}
