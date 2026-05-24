# 基岩版模型渲染方案说明

## 目标

为当前 UI 运行时新增一条“基岩版模型渲染”路径，使自定义 Bedrock 几何模型能够替换现有的原版 `entity` 预览渲染。该能力面向 Minecraft `1.21.11`，目标覆盖以下场景：

- 在需要时仍然绑定实体目标，但显示的模型可被自定义 Bedrock 几何替换。
- 可以按名称播放指定动画片段。
- 可以通过 YAML 动作或 JS 在运行时切换模型与动画。
- 顶点上传成本足够低，能用于 HUD 和 GUI 场景。

## 在现有运行时中的位置

- 保留当前 `entity` 元素，继续作为原版实体预览的回退方案。
- 为 Bedrock 路径新增一种 UI 元素类型：
  - `bedrock_model`
- Bedrock 渲染器应与 `UiScreen` 并列接入，而不是继续塞进现有 `InventoryScreen.renderEntityInInventoryFollowsMouse(...)` 这条原版实体渲染路径。
- 建议将 Bedrock 运行时拆成以下部分：
  - geometry loader
  - animation loader
  - runtime state
  - renderer
  - shader assets

## 建议的资源目录

- 几何体：
  - `assets/<modid>/bedrock/models/<name>.geo.json`
- 动画：
  - `assets/<modid>/bedrock/animations/<name>.animation.json`
- 纹理：
  - `assets/<modid>/textures/bedrock/<name>.png`

## 建议的 YAML Schema

```yaml
- id: "boss_preview"
  type: "bedrock_model"
  x: "50%"
  y: "55%"
  width: 160
  height: 160
  anchor_x: "center"
  anchor_y: "center"
  model: "magicmod:bedrock/models/phoenix.geo.json"
  texture: "magicmod:textures/bedrock/phoenix.png"
  animation_file: "magicmod:bedrock/animations/phoenix.animation.json"
  animation: "animation.phoenix.idle"
  replace_entity_model: true
  target_type: "player"
  look_at_mouse: true
  model_scale: 1.0
  render_mode: "cutout"
  cull: false
  z: 8
```

### 新增字段

- `type: "bedrock_model"`
- `model`
  - Bedrock 几何体 JSON 资源。
- `texture`
  - 几何体使用的纹理资源。
- `animation_file`
  - Bedrock 动画 JSON 资源。
- `animation`
  - 默认播放的动画片段名称。
- `replace_entity_model`
  - 为 `true` 时，仅把实体状态当作数据来源，真正显示的网格替换为 Bedrock 模型。
- `model_scale`
  - 每个元素自己的模型缩放倍率。
- `render_mode`
  - 可选 `cutout`、`translucent`、`emissive_cutout`。
- `cull`
  - 是否开启模型面的剔除。

## 建议的 JS API

```javascript
ui.replaceEntityModel(id, model, texture)
ui.setModelAnimationFile(id, animationFile)
ui.playModelAnimation(id, animationName)
ui.playModelAnimation(id, animationName, true)
ui.playModelAnimation(id, animationName, true, 150)
ui.stopModelAnimation(id)
ui.setModel(id, "magicmod:bedrock/models/phoenix.geo.json", "magicmod:textures/bedrock/phoenix.png")
```

### 运行时语义

- `replaceEntityModel`
  - 保留逻辑上的实体绑定关系，但渲染时切换为自定义网格。
- `playModelAnimation`
  - 启动指定名称的动画片段。
  - 可选布尔参数控制是否循环。
  - 可选淡入时间参数控制混合过渡，单位毫秒。
- `stopModelAnimation`
  - 停止当前动画，并回到 bind pose 或配置好的 idle pose。

## 渲染流水线

### 阶段 1：加载

- 解析 `.geo.json`，生成不可变几何数据：
  - bones
  - pivot
  - rotation
  - cubes
  - UV boxes
- 解析 `.animation.json`，生成动画片段数据：
  - timeline channels
  - interpolation mode
  - per-bone keyframes

### 阶段 2：烘焙

- 将每个 cube 一次性转换为静态 vertex/index buffer。
- 把 cube 面展开为稳定的 bind-pose 网格。
- 预计算：
  - 局部 cube 变换
  - 面法线
  - UV 矩形
  - 骨骼归属

### 阶段 3：动画

- 每帧只评估骨骼变换。
- 上传 bone palette，而不是每帧重建整份顶点数据。
- 按骨骼做动画混合，而不是重新烘焙几何体。

### 阶段 4：绘制

- 使用专用 shader 路径，把烘焙后的网格渲染到 UI 区域。
- 将模型投影到 `UiScreen` 中的目标矩形区域。
- 当多个 Bedrock 模型元素共享同一纹理时，尽量复用纹理绑定。

## 优化策略

### 顶点优化

- 每个 geometry asset 只保留一份静态 bind-pose VBO 和 IBO。
- 顶点中只存储：
  - position
  - normal
  - uv
  - bone index
- 避免在每帧动画时走 CPU 侧顶点重建。
- 在几何规模允许的情况下优先使用 16-bit indices。

### 动画优化

- 资源加载时就把动画片段预采样到紧凑的运行时缓存中。
- 压缩冗余关键帧：
  - 删除重复帧
  - 合并平坦曲线
  - 在可接受范围内量化 rotation / translation 通道
- 以下情况可跳过骨骼求值：
  - 隐藏骨骼
  - 没有可见 cube 绑定的骨骼
  - 当前模型下没有活跃通道的动画片段

### 绘制优化

- 按以下维度做批处理：
  - shader
  - texture
  - render mode
- 尽量保持“一模型一纹理”。
- 把 GUI 投影相关 uniform 更新放在内层绘制循环之外。
- 为未来预留 instanced Bedrock model draw 路径，使同一模型可用不同变换重复绘制。

## Shader 设计

### 顶点输入

- `in vec3 Position`
- `in vec3 Normal`
- `in vec2 UV0`
- `in uint BoneIndex`

### Uniforms

- `mat4 uProjection`
- `mat4 uView`
- `mat4 uModel`
- `mat4 uBones[MAX_BONES]`
- `vec4 uTint`
- `float uAlpha`

### 顶点着色器骨架

```glsl
#version 150

in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in float BoneIndex;

uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;
uniform mat4 uBones[128];

out vec2 vUv;
out vec3 vNormal;

void main() {
    int bone = int(BoneIndex);
    mat4 skin = uBones[bone];
    vec4 worldPos = uModel * skin * vec4(Position, 1.0);
    gl_Position = uProjection * uView * worldPos;
    vUv = UV0;
    vNormal = mat3(uModel * skin) * Normal;
}
```

### 片元着色器骨架

```glsl
#version 150

uniform sampler2D Sampler0;
uniform vec4 uTint;
uniform float uAlpha;

in vec2 vUv;
in vec3 vNormal;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, vUv);
    if (tex.a <= 0.001) {
        discard;
    }

    float lambert = clamp(dot(normalize(vNormal), normalize(vec3(0.2, 0.8, 0.4))), 0.35, 1.0);
    vec4 color = tex * uTint;
    fragColor = vec4(color.rgb * lambert, color.a * uAlpha);
}
```

## 动画播放规则

- 默认状态：
  - bind pose，或配置好的 idle animation
- 片段播放模式：
  - `play once`
  - `loop`
  - `blend from current`
- 推荐运行时行为：
  - 保持一个当前激活动画和一个正在淡出的动画
  - 在骨骼变换层做混合
  - 动画 tick 过程中不要分配新的每帧对象

## 替换语义

当 `replace_entity_model: true` 时：

- 保留来自实体目标的数据，例如：
  - player name
  - facing
  - selected animation state mapping
  - target-specific data tokens
- 真正显示时使用配置的 Bedrock geometry 替换原版实体网格。
- 该元素不再调用原版 inventory entity renderer。

## 交付计划

1. 新增不可变 Bedrock geometry 和 animation parser。
2. 新增 `bedrock_model` schema 字段与运行时状态容器。
3. 新增静态 baked mesh 路径与 bone palette 上传逻辑。
4. 新增 shader 资源并接入 `UiScreen` 渲染流程。
5. 新增 JS 层的模型替换与动画播放辅助 API。
6. 提供一份 demo YAML 和一份性能压测样例。

## 验收标准

- UI 元素可以在原版实体预览位置渲染一份 Bedrock geometry。
- 可以通过 YAML 或 JS 按名称播放指定动画片段。
- 动画播放过程中不会每帧重建整份顶点缓冲。
- 多个相同模型元素能够复用已缓存的 baked data。
- 资源重载时，geometry、animation 和 texture cache 都能被正确失效与重建。
