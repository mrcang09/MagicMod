# UI纹理图集与GPU实例化渲染实现指南

## 概述

本文档记录了UI系统中纹理图集（Texture Atlas）和GPU实例化渲染（GPU Instancing）的完整实现，用于大幅降低drawcall数量，提升渲染性能。

## 核心组件

### 1. UiTextureAtlas - 纹理图集管理器

**文件**: `src/main/java/org/test/magicmod/ui/UiTextureAtlas.java`

**功能**:
- 使用shelf bin packing算法将多个小纹理打包到一张大图集中
- 默认图集大小：128x128（可配置）
- 记录每个纹理在图集中的位置（UV坐标）
- 生成DynamicTexture并上传到GPU

**关键方法**:
```java
public boolean build(List<String> texturePaths)  // 构建图集
public ResourceLocation getAtlasLocation()        // 获取图集纹理位置
public AtlasRegion getRegion(String texturePath) // 获取纹理在图集中的区域
public void cleanup()                             // 清理GPU资源
```

**实现细节**:
- 使用NativeImage读取源纹理
- 使用简单货架装箱算法（shelf packing）
- 动态创建纹理并注册到TextureManager
- 支持GIF文件跳过（需特殊处理）

### 2. UiInstancedRenderer - GPU实例化渲染器

**文件**: `src/main/java/org/test/magicmod/ui/UiInstancedRenderer.java`

**功能**:
- 使用OpenGL 3.3的`glDrawArraysInstanced`批量渲染
- 一次drawcall渲染多个四边形实例
- 每个实例有独立属性：位置、大小、UV、颜色、透明度

**顶点布局**:
```
基础四边形顶点 (per-vertex):
- location 0: vec2 position    // 归一化位置 [0,1]
- location 1: vec2 baseUV      // 基础UV坐标
- location 2: float texIndex   // 纹理索引（预留）

实例属性 (per-instance, divisor=1):
- location 3: vec2 instPos     // 实例屏幕位置
- location 4: vec2 instSize    // 实例大小
- location 5: vec2 instUVOff   // UV偏移
- location 6: vec2 instUVScale // UV缩放
- location 7: vec4 instColor   // 实例颜色
- location 8: float instAlpha  // 实例透明度
```

**关键方法**:
```java
public void init()              // 初始化VBO/VAO
public void begin()             // 开始新批次
public void addInstance(...)    // 添加实例
public void render()            // 渲染所有实例
public void cleanup()           // 清理GPU资源
```

### 3. UiConfig - 配置支持

**文件**: `src/main/java/org/test/magicmod/ui/UiConfig.java`

**新增字段**:
```java
public final boolean useAtlas;   // 是否使用图集
public final int atlasSize;      // 图集大小（默认128）
```

**YAML配置**:
```yaml
use_atlas: true      # 启用图集模式
atlas_size: 128      # 图集大小
shader_render: true  # 启用shader渲染（实例化渲染需要）
```

### 4. UiScreen - 渲染集成

**文件**: `src/main/java/org/test/magicmod/ui/UiScreen.java`

**集成流程**:

1. **初始化阶段** (`init()`):
```java
initializeAtlas()              // 收集IMAGE元素纹理，构建图集
initializeInstancedRenderer()  // 初始化实例化渲染器
```

2. **渲染阶段** (`renderOverlay()`):
```java
if (config.useAtlas && config.shaderRender && instancedRenderer != null) {
    renderWithInstancing()  // 使用实例化渲染
} else {
    // 传统逐元素渲染
}
```

3. **实例化渲染** (`renderWithInstancing()`):
```java
instancedRenderer.begin();
for (element : elements) {
    if (element.type == RECT) {
        instancedRenderer.addInstance(...);  // 批量收集
    } else {
        renderElement(...);  // 非RECT元素正常渲染
    }
}
instancedRenderer.render();  // 一次drawcall渲染所有实例
```

4. **清理阶段** (`removed()`):
```java
cleanupAtlasAndRenderer()  // 释放图集和渲染器的GPU资源
```

## 性能优化原理

### 传统渲染 (CPU渲染 + 逐元素)
- 500个元素 = 500次drawcall
- 每个元素单独设置状态和绘制
- CPU开销大，GPU利用率低

### Shader渲染 (无图集)
- 500个元素 = 500次drawcall
- 使用VBO批处理，减少CPU开销
- 仍然有大量drawcall

### 图集 + 实例化渲染
- 500个元素 = **1次drawcall**
- 所有实例共享同一个图集纹理
- 使用GPU并行处理实例属性
- CPU开销最小，GPU利用率最高

## 性能测试配置

### 测试文件（每个500个RECT元素）

1. **perf_test_shader_atlas.yml**
```yaml
shader_render: true
use_atlas: true
atlas_size: 128
# 预期：最高FPS，1次drawcall
```

2. **perf_test_shader_no_atlas.yml**
```yaml
shader_render: true
use_atlas: false
# 预期：中等FPS，500次drawcall
```

3. **perf_test_no_shader.yml**
```yaml
shader_render: false
use_atlas: false
# 预期：最低FPS，500次drawcall + CPU开销
```

### 测试命令
```
/open perf_test_shader_atlas.yml
/open perf_test_shader_no_atlas.yml
/open perf_test_no_shader.yml
```

### 性能指标
- 按F3查看FPS
- 对比三种模式的帧率差异
- 理论性能排序：图集+实例化 > Shader > CPU渲染

## 实现注意事项

### 1. OpenGL版本要求
- 需要OpenGL 3.3+
- `glDrawArraysInstanced` (GL 3.1+)
- `glVertexAttribDivisor` (GL 3.3+)

### 2. 图集大小限制
- 默认128x128，避免过大图集浪费显存
- 使用shelf packing算法，装箱效率约70-80%
- 超出图集大小的纹理会被跳过

### 3. 支持的元素类型
- 当前实例化渲染仅支持RECT类型
- IMAGE类型可以使用图集，但需要额外实现
- TEXT、PROGRESS等元素继续使用传统渲染

### 4. Shader要求
- 当前使用固定管线渲染（无自定义shader）
- 完整实例化渲染需要自定义顶点shader
- 需要处理实例属性变换（位置、UV、颜色）

### 5. 资源管理
- 图集和渲染器在UiScreen.removed()时自动清理
- 避免内存泄漏和显存泄漏
- DynamicTexture会自动从TextureManager注销

## 扩展方向

### 1. 自定义Shader支持
```glsl
// 顶点shader示例
#version 330 core
layout(location = 0) in vec2 position;
layout(location = 1) in vec2 baseUV;
layout(location = 3) in vec2 instPos;
layout(location = 4) in vec2 instSize;
layout(location = 5) in vec2 instUVOff;
layout(location = 6) in vec2 instUVScale;
layout(location = 7) in vec4 instColor;
layout(location = 8) in float instAlpha;

out vec2 vUV;
out vec4 vColor;

uniform mat4 uProjection;

void main() {
    vec2 worldPos = instPos + position * instSize;
    gl_Position = uProjection * vec4(worldPos, 0.0, 1.0);
    vUV = instUVOff + baseUV * instUVScale;
    vColor = vec4(instColor.rgb, instColor.a * instAlpha);
}
```

### 2. 支持更多元素类型
- IMAGE元素实例化渲染
- TEXT元素使用字体图集
- 自定义形状（圆形、三角形等）

### 3. 动态图集
- 运行时动态添加/移除纹理
- 图集碎片整理
- 多图集支持（超过128x128时）

### 4. 高级批处理
- 按纹理分组批处理
- Z-order排序优化
- 透明度混合优化

## 相关提交

- `54042da` - 恢复F3调试界面和启用shader渲染
- `d76d06d` - 添加图集和GPU实例化基础设施
- `905c864` - 集成图集和实例化渲染到UiScreen

## 参考资料

- OpenGL Instancing: https://www.khronos.org/opengl/wiki/Vertex_Rendering#Instancing
- Texture Atlas: https://en.wikipedia.org/wiki/Texture_atlas
- Bin Packing Algorithms: https://github.com/juj/RectangleBinPack
