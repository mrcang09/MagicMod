#version 150

// 输入：顶点属性
in vec3 Position;     // 顶点位置 (x, y, z)
in vec4 Color;        // 顶点颜色 (r, g, b, a)
in vec2 UV0;          // 纹理坐标 (u, v)

// 输出到片元着色器
out vec4 vertexColor;
out vec2 texCoord;

// Uniform变量
uniform mat4 ModelViewMat;    // 模型视图矩阵
uniform mat4 ProjMat;         // 投影矩阵
uniform vec3 WorldPos;        // 世界坐标（用于偏移）

// 变换矩阵参数（从CPU传入）
uniform vec2 Translation;     // 平移 (tx, ty)
uniform float Rotation;       // 旋转角度（弧度）
uniform vec2 Scale;           // 缩放 (sx, sy)
uniform vec2 Pivot;           // 旋转中心点 (px, py)

void main() {
    // 1. 构建旋转矩阵
    float cosR = cos(Rotation);
    float sinR = sin(Rotation);
    mat2 rotationMat = mat2(
        cosR, sinR,
        -sinR, cosR
    );

    // 2. 应用变换：先平移到旋转中心，旋转，缩放，平移回来，再应用Translation
    vec2 localPos = Position.xy;

    // 移动到旋转中心
    localPos -= Pivot;

    // 应用旋转
    localPos = rotationMat * localPos;

    // 应用缩放
    localPos *= Scale;

    // 移回原位并应用额外平移
    localPos += Pivot + Translation;

    // 3. 应用世界坐标偏移
    vec3 worldPosition = vec3(localPos, Position.z) + WorldPos;

    // 4. 应用MVP矩阵变换
    gl_Position = ProjMat * ModelViewMat * vec4(worldPosition, 1.0);

    // 5. 传递数据到片元着色器
    vertexColor = Color;
    texCoord = UV0;
}
