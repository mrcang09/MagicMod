# MagicMod

一个基于 **Minecraft Forge 1.21.8** 的实验性 Mod，核心目标是提供「类似 DragonCore 的 YAML 驱动 UI 系统」，支持快速热迭代、脚本交互和可选 GPU 渲染优化。

## 项目定位

- 用 YAML 描述 UI（界面、HUD、容器覆盖层）。
- 用 JavaScript（Rhino）处理点击、悬停、打开/关闭等事件动作。
- 通过 `/open <ui.yml>` 在客户端快速预览 UI。
- 支持传统渲染、Shader 渲染，以及纹理图集 / 批量渲染相关优化路径。

> 这个仓库目前既包含 Forge 模板示例内容（例如 `example_block`、`example_item`），也包含完整的自定义 UI 系统实现。

## 技术栈与版本

- Minecraft: `1.21.8`
- Forge: `58.1.11`
- Java: `21`
- 构建工具: Gradle（ForgeGradle 6）
- 关键依赖：
  - `org.yaml:snakeyaml:2.2`（YAML 解析）
  - `org.mozilla:rhino:1.7.14`（JS 脚本执行）

## 快速开始

### 1) 环境要求

- JDK 21
- 能正常拉取 Forge 相关依赖的网络环境

### 2) 本地运行客户端

```bash
./gradlew runClient
```

Windows 下也可以参考仓库中的 bat 脚本（需自行改 `JAVA_HOME` 路径）：

- `run_client_java.bat`
- `run_build.bat`

### 3) 打包

```bash
./gradlew build
```

## UI 系统使用方式

### 1) UI 配置文件位置

内置示例 UI 位于：

- `src/main/resources/assets/magicmod/ui/`

示例包括：

- `loading.yml`
- `inventory.yml`
- `chest.yml`
- `esc.yml`
- `hud.yml`
- `shader_demo.yml`
- `perf_test_*.yml`

### 2) 游戏内打开 UI

客户端命令：

```text
/open <ui文件名>
```

例如：

```text
/open loading.yml
/open shader_demo.yml
```

命令带有 UI 文件自动建议，输入不带后缀时会自动补 `.yml`。

### 3) UI 读取优先级（开发友好）

`UiLoader` 会优先尝试从工程磁盘路径读取（便于开发时快速改 YAML），找不到再回退到资源包路径读取。

## 核心能力概览

### YAML 驱动界面

- 顶层配置支持：`allow_move`、`override_esc`、`hud`、`replace_hud`、`replace_vanilla`、`shader_render`、`use_atlas`、`events`、`animations` 等。
- 元素类型支持：
  - `text`
  - `image`（含 GIF）
  - `rect`
  - `progress`
  - `scroll` / `scroll_list`
  - `slot`
  - `entity`
- 位置支持数值、百分比，以及表达式（如 `0.5*w` / `0.5*h`）。
- 支持 `anchor_x` / `anchor_y` 独立锚点控制。

### 脚本系统（Rhino JS）

UI 动作脚本可直接调用 `ui` API，例如：

- `ui.open(name)` / `ui.close()`
- `ui.show(id)` / `ui.hide(id)` / `ui.toggle(id)`
- `ui.set(id, property, value)`
- `ui.create(parentId, element)` 动态创建元素
- `ui.playAnimation(id)` / `ui.stopAnimation(id)`
- `ui.playMusic(...)` / `ui.stopMusic(...)`
- `ui.getHealth()`、`ui.getFood()`、`ui.getTargetEntityName()` 等状态读取

### 覆盖层与替换

- 容器 UI 可按标题匹配覆盖（`match_titles`）并支持替换原版渲染（`replace_vanilla`）。
- 支持 HUD 覆盖与替换（`hud` / `replace_hud`）。
- 提供 `esc.yml` 用于替换暂停界面场景。

### 渲染优化

- 可切换 `shader_render` 走 GPU 渲染路径。
- 支持 `use_atlas` + `atlas_size` 纹理图集。
- 仓库包含性能对比样例：
  - `perf_test_no_shader.yml`
  - `perf_test_shader_no_atlas.yml`
  - `perf_test_shader_atlas.yml`

## 项目结构（建议先看）

```text
src/main/java/org/test/magicmod/
├── Magicmod.java                 # Mod入口与注册
├── audio/AudioController.java    # UI音乐控制
├── client/
│   ├── ClientUiCommands.java     # /open 命令注册
│   ├── UiOverlayManager.java     # 容器覆盖层管理
│   └── UiHudManager.java         # HUD管理
├── mixin/                        # 原版界面渲染拦截
└── ui/                           # YAML UI 运行时（加载/渲染/脚本/动画）

src/main/resources/
├── assets/magicmod/ui/           # UI YAML 示例
├── assets/magicmod/shaders/      # UI shader
├── assets/magicmod/music/        # UI 音乐
└── META-INF/mods.toml
```

## 文档索引

- `knowledge_base/README.md`：知识库入口
- `knowledge_base/purpose.md`：项目目标
- `knowledge_base/changes.md`：迭代记录
- `knowledge_base/ui_schema.md`：YAML 字段说明
- `docs/TEXTURE_ATLAS_INSTANCING.md`：图集与实例化渲染实现说明

## 当前状态与建议

- 这是一个功能较完整的 UI 框架雏形，适合做「Minecraft 界面中台」方向继续扩展。
- 若要继续工程化，建议优先做：
  1. README 中补一份最小可运行 YAML + JS 示例。
  2. 增加 UI 配置校验与错误定位（行号级）。
  3. 增加 automated tests（至少对 YAML 解析、表达式求值和动作脚本 API 做单测）。
  4. 补充面向“模组使用者”与“二次开发者”的分层文档。
