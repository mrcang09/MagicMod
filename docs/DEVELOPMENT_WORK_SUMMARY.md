# MagicMod 阶段开发整理与提交说明

## 1. 本阶段目标

本阶段围绕 MagicMod 的客户端能力扩展、模型替换系统、基岩版模型渲染、FBX/MMESH 接入、命令链路、运行环境修复、测试验证和交付文档进行整理与开发。

核心目标：

- 梳理项目模块、资源加载链路、客户端渲染链路和命令调用链。
- 修复运行脚本与本地 JDK 环境问题，确保 Forge `1.21.11` 可以构建和启动。
- 完善类似龙核的实体模型替换策划方案，并落地客户端本地验证闭环。
- 支持将实体模型替换为测试 runtime 模型、Bedrock `geo.json` 模型、ASCII FBX 演示模型和 `.mmesh` 中间格式模型。
- 支持通过命令播放、停止、重置指定模型动画。
- 重点检查资源缓存、runtime 实例、GPU buffer 释放，降低内存泄漏风险。
- 生成中文测试报告、DOCX 报告、截图附图和最终验证记录。

## 2. 已完成的主要开发内容

### 2.1 构建与环境

- 新增/调整本地 Java 解析脚本，自动定位可用 JDK 21。
- 修复 `run_build.bat`、`run_client_java.bat` 等脚本，使本地构建和客户端启动流程更稳定。
- 将项目运行目标整理到 Minecraft Forge `1.21.11`。
- 多次执行 `cmd /c run_build.bat`，最终构建通过。

### 2.2 项目审计与文档

- 新增 `docs/PROJECT_AUDIT_AND_ROADMAP.md`，整理项目模块、当前状态、风险点和路线图。
- 新增 `docs/BEDROCK_MODEL_RENDERING_SPEC.md`，整理 Bedrock 模型渲染、动画、shader 和性能优化方案。
- 新增 `docs/ENTITY_MODEL_REPLACEMENT_AND_ANIMATION_PLAN.md`，整理实体模型替换、Bedrock/FBX 动画、命令设计和阶段规划。
- 新增 `docs/MODEL_REPLACEMENT_TEST_REPORT.md`，记录真实进游戏测试结果。
- 新增 `docs/MODEL_REPLACEMENT_TEST_REPORT.docx`，导出中文 DOCX 测试报告。
- 新增 `docs/model_report_render/page-*.png`，用于 DOCX 渲染后的页面检查。
- 新增本文件，作为本阶段提交总说明。

### 2.3 模型替换系统

- 新增 `org.test.magicmod.model` 模型运行时模块。
- 新增统一的 `ModelAssetSpec`、`ModelAssetFormat`、`RuntimeModel`、`RuntimeMeshPart`、`RuntimeSkeleton`、`RuntimeAnimationClip` 等运行时抽象。
- 新增 `ModelReplacementTarget` 和 `ModelReplacementTargetKind`，支持多种替换目标：
  - `entity_type`
  - 精确实体实例
  - `uuid`
  - `player_name`
  - `tag`
- 修复 `replaceType(Identifier, Identifier)` 忽略 `modelId` 的问题。
- 将 `/magicmodel client replace target` 从“替换实体类型”修正为“替换当前准星实体或本地玩家实体本身”。
- 新增 `ModelReplacementManager` 管理手动规则、配置规则、runtime 实例和调试统计。
- 新增 `ModelCache` 负责模型缓存、加载、reload 和释放。
- 新增 `CustomEntityModelRenderer` 接入 Forge LivingEntity 渲染前事件，实现替换模型渲染。
- 新增客户端 tick 剪枝，定期清理已经离开世界的实体 runtime 实例。

### 2.4 Bedrock / FBX / MMESH 接入

- 新增 `BedrockModelLoader`，支持 Bedrock `geo.json` 与 `animation.json` 的加载、解析和基础动画播放。
- 新增 `FbxModelLoader`，当前支持 ASCII FBX 静态网格演示模型导入，并接入统一 runtime 与 fallback 动画。
- 新增 `MmeshModelLoader`，支持 `.mmesh` 中间运行时格式加载。
- 新增测试资源：
  - `assets/magicmod/entity_models/bedrock/test_creature.geo.json`
  - `assets/magicmod/entity_models/bedrock/test_creature.animation.json`
  - `assets/magicmod/entity_models/fbx/test_cube_ascii.fbx`
  - `assets/magicmod/entity_models/runtime/test_cube.mmesh`
  - `assets/magicmod/model_replacements/demo_pig_bedrock.json`

## 3. 指令与调用链

### 3.1 替换模型指令

```text
/magicmodel client replace target magicmod:test_cube
/magicmodel client replace target bedrock <geo> <texture> [animation_file]
/magicmodel client replace target fbx <model> <texture> [animation_file]
/magicmodel client replace target mmesh <model> <texture> [animation_file]
```

实体类型替换：

```text
/magicmodel client replace type <entity_type> magicmod:test_cube
/magicmodel client replace type <entity_type> bedrock <geo> <texture> [animation_file]
/magicmodel client replace type <entity_type> fbx <model> <texture> [animation_file]
/magicmodel client replace type <entity_type> mmesh <model> <texture> [animation_file]
```

### 3.2 动画指令

```text
/magicmodel client animation play target <animation> [loop|once]
/magicmodel client animation stop target
/magicmodel client animation reset target
/magicmodel client animation play type <entity_type> <animation> [loop|once]
/magicmodel client animation stop type <entity_type>
/magicmodel client animation reset type <entity_type>
```

### 3.3 调用链摘要

```text
客户端命令
  -> ClientModelReplacementSystem
  -> ModelReplacementManager
  -> ModelCache
  -> BedrockModelLoader / FbxModelLoader / MmeshModelLoader
  -> RuntimeModel / RuntimeAnimationClip / ModelRuntimeInstance
  -> RenderLivingEvent.Pre
  -> CustomEntityModelRenderer
  -> 替换原版实体模型渲染
```

资源释放链路：

```text
/magicmodel client clear target
  -> ModelReplacementManager.clearEntity(...)
  -> 清理对应 runtime instance

/magicmodel reload 或资源 reload
  -> ModelReplacementManager.reloadResources(...)
  -> RuntimeModel.close()
  -> RuntimeMeshPart.close()
  -> GpuMeshBuffer.close()
```

## 4. 内存泄漏与资源释放检查

本阶段重点检查并修复了以下风险点：

- runtime 实例不持有 `Entity` 强引用，只保存 session/entity id、uuid、player name、tag 等轻量 key。
- reload 时会关闭所有已缓存 `RuntimeModel`，并递归关闭 mesh part 和 GPU buffer。
- `GpuMeshBuffer` 使用 `closed` 标记防止重复释放导致计数异常。
- 客户端退出世界时调用 `clearSession()`，清理手动规则、runtime 实例和模型缓存。
- 修复实体级 runtime 实例剪枝 key 前缀不一致的问题，避免实体消失后残留 runtime 实例。
- 游戏内最终验证显示：
  - `runtimeInstances=0`
  - `loadedModels=0`
  - `liveGpuBuffers=0`

## 5. 实际游戏内验证

最新一次真实验证使用如下参数启动客户端：

```text
-Dmagicmod.smokeTestModel=true
-Dmagicmod.smokeTestExit=true
```

验证方式：

- 启动 Forge `1.21.11` 客户端。
- 进入单人世界。
- 自动执行模型替换烟测。
- 自动保存截图。
- 自动清理资源并退出客户端。

最新验证日志：

```text
D:\MagicMod\runclient.stage2memfix.stdout.log
```

关键结果：

```text
[MODEL_SMOKE] /magicmodel client replace target magicmod:test_cube
[MODEL_SMOKE] Replacement rendered in-world; before=0, after=1386
[MODEL_SMOKE] /magicmodel client replace target bedrock ...
[MODEL_SMOKE] Bedrock replacement rendered in-world; before=2794, after=4198
[MODEL_SMOKE] Tagged player and replaced tag with ASCII FBX demo model
[MODEL_SMOKE] FBX replacement rendered in-world; before=5604, after=7010
[MODEL_SMOKE] /magicmodel client replace target mmesh ...
[MODEL_SMOKE] MMESH replacement rendered in-world; before=7716, after=9128
[MODEL_SMOKE] Cache released cleanly: typeRules=1, runtimeInstances=0, loadedModels=0, liveGpuBuffers=0, reloadVersion=3, renderedReplacements=9828
BUILD SUCCESSFUL in 5m 25s
```

最终截图：

```text
D:\MagicMod\docs\model_replacement_stage2_screenshot.png
```

## 6. 已知边界

以下能力已经在方案中设计，但没有在本阶段伪装成已完成：

- 完整二进制 FBX 骨骼动画导入。
- 服务端到客户端的模型替换同步网络包。
- 多客户端动画状态同步。
- shader GPU skinning。
- 动画混合树、IK、挂点系统。
- 完整离线 `.fbx -> .mmesh` 转换工具链。

当前已跑通的是客户端本地模型替换主闭环，以及 Bedrock 动画、ASCII FBX 演示、MMESH 中间格式、命令控制和资源释放验证。

## 7. 本次提交建议说明

建议提交信息：

```text
feat: 完成模型替换运行时与游戏内验证文档
```

本次提交包含代码、资源、中文策划文档、中文测试报告、DOCX 报告、渲染检查页和游戏内截图。
