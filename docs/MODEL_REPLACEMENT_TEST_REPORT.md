# MagicMod 模型替换功能测试报告

## 基本信息

- 测试日期：2026-04-26
- 目标版本：Minecraft Forge `1.21.11`
- 测试方式：真实启动客户端，进入单人世界，执行 `-Dmagicmod.smokeTestModel=true` 自动烟测。
- 最终日志：`D:\MagicMod\runclient.stage2memfix.stdout.log`
- 最终截图：`D:\MagicMod\docs\model_replacement_stage2_screenshot.png`

## 本次代码审计结论

本轮重点检查了模型替换主链路、命令注册、资源缓存、运行时动画状态和导入器代码。

已修复或优化的问题：

- 修复 `replaceType(Identifier, Identifier)` 忽略 `modelId` 的问题，现在会通过 `ModelCache.specFor(...)` 正确解析模型规格。
- 将 `/magicmodel client replace target` 从“替换目标实体类型”修正为“替换当前准星实体或本地玩家实体本身”。
- 新增统一替换目标抽象，支持 `entity_type`、精确实体、`uuid`、`player_name`、`tag` 规则。
- 新增 `mmesh` 中间运行时模型格式，作为 FBX 离线转换和运行时快速加载的落地入口。
- 清理未使用的格式建议常量和旧版 String 参数命令处理函数，降低命令注册代码冗余。
- 运行时实例不持有实体对象，只使用 session/entity id、玩家名、标签等轻量 key，退出世界和 reload 后清理。
- 修复实体级 runtime 实例剪枝前缀不一致的问题，避免未显式 clear 的单实体替换在实体消失后残留。
- 骨骼动画采样路径复用 scratch 向量，避免每帧持续创建临时 keyframe 对象。

仍需明确的边界：

- 当前 FBX 支持为 ASCII FBX 静态网格导入，并接入统一 runtime 与 fallback 动画，不等同于完整二进制 FBX 骨骼动画导入。
- 完整服务端同步命令、网络包、多客户端动画同步仍应作为下一轮网络阶段实现，不能用客户端本地命令假装完成。
- Shader GPU skinning、动画混合树、IK、挂点系统属于高级阶段，当前已完成 CPU runtime 骨骼采样和基础动画播放闭环。

## 实际测试指令与效果

| 步骤 | 指令或动作 | 预期效果 | 实测结果 |
|---|---|---|---|
| 1 | `/magicmodel client replace target magicmod:test_cube` | 将当前玩家实体精确替换为测试立方体 | 成功，日志显示 target entity 替换，渲染计数从 0 增长到 1386 |
| 2 | `/magicmodel client animation play target spin loop` | 对当前替换实体循环播放旋转动画 | 成功，命令被客户端处理并持续渲染 |
| 3 | `/magicmodel client animation play target pulse once` | 对当前替换实体播放一次 pulse 动画 | 成功，命令被客户端处理 |
| 4 | `/magicmodel client clear target` | 清理当前实体替换，恢复原版模型 | 成功，日志显示清理 entity replacement |
| 5 | `/magicmodel client replace target bedrock magicmod:entity_models/bedrock/test_creature.geo.json minecraft:textures/block/copper_block.png magicmod:entity_models/bedrock/test_creature.animation.json` | 将当前实体替换为 Bedrock 几何模型并绑定动画文件 | 成功，Bedrock 替换渲染计数从 2794 增长到 4198 |
| 6 | `/magicmodel client animation play target animation.magicmod.test_creature.spin loop` | 播放 Bedrock 动画文件中的指定动画 | 成功，日志显示 Bedrock animation 播放并完成渲染 |
| 7 | `/magicmodel client animation stop target` | 停止当前实体动画 | 成功，命令被客户端处理 |
| 8 | `/magicmodel client animation reset target` | 重置当前实体动画状态 | 成功，命令被客户端处理 |
| 9 | 自动给玩家添加 `magicmod_smoke_target` 标签并调用 tag 规则 | 验证标签目标规则可以命中实体 | 成功，FBX 演示模型通过 tag 规则渲染，计数从 5604 增长到 7010 |
| 10 | `/magicmodel client replace target mmesh magicmod:entity_models/runtime/test_cube.mmesh minecraft:textures/block/copper_block.png` | 加载 `.mmesh` 中间格式并替换当前实体 | 成功，MMESH 渲染计数从 7716 增长到 9128 |
| 11 | `/magicmodel client animation play target spin once` | 对 MMESH 模型播放一次 fallback spin 动画 | 成功，命令被客户端处理 |
| 12 | `/magicmodel client clear target` + reload | 清理替换、重载资源并释放缓存 | 成功，`runtimeInstances=0, loadedModels=0, liveGpuBuffers=0` |

## 游戏内验证结果

最终验证完整进入了单人世界，烟测序列完成后客户端主动关闭。

关键日志证据：

```text
[MODEL_SMOKE] Singleplayer world detected; starting model smoke sequence
[MODEL_SMOKE] /magicmodel client replace target magicmod:test_cube
[MODEL_SMOKE] Replacement rendered in-world; before=0, after=1386
[MODEL_SMOKE] /magicmodel client replace target bedrock ...
[MODEL_SMOKE] Bedrock replacement rendered in-world; before=2794, after=4198
[MODEL_SMOKE] Tagged player and replaced tag with ASCII FBX demo model
[MODEL_SMOKE] FBX replacement rendered in-world; before=5604, after=7010
[MODEL_SMOKE] /magicmodel client replace target mmesh ...
[MODEL_SMOKE] MMESH replacement rendered in-world; before=7716, after=9128
[MODEL_SMOKE] Saved verification screenshot to D:\MagicMod\docs\model_replacement_stage2_screenshot.png
[MODEL_SMOKE] Cache released cleanly: typeRules=1, runtimeInstances=0, loadedModels=0, liveGpuBuffers=0, reloadVersion=3, renderedReplacements=9828
[MODEL_SMOKE] Completed model replacement smoke sequence
BUILD SUCCESSFUL in 5m 25s
```

## 内存与资源释放检查

- 构建结果：`BUILD SUCCESSFUL`
- 客户端退出：无残留 `java/javaw` 进程
- 崩溃报告：未生成 `run/crash-reports`
- GPU buffer 统计：最终 `liveGpuBuffers=0`
- Runtime 实例统计：最终 `runtimeInstances=0`
- 模型缓存统计：最终 `loadedModels=0`
- 补丁格式检查：`git diff --check` 仅有 CRLF 换行提示，无尾随空格或冲突标记

## 截图说明

截图文件：`D:\MagicMod\docs\model_replacement_stage2_screenshot.png`

截图内容为烟测 MMESH 阶段，由游戏内部截图 API 保存。画面中玩家实体已经被铜块纹理的 `.mmesh` runtime cube 覆盖，视角贴近模型表面，证明自定义模型替换在真实渲染管线中生效。

## 结论

本轮已跑通客户端本地模型替换主闭环：精确实体替换、实体类型替换基础、Bedrock 模型加载、Bedrock 动画播放、FBX 演示导入、tag 规则命中、MMESH 中间格式加载、指令播放/停止/重置动画、reload 清理和退出释放。

下一轮若继续推进，建议优先实现真正的服务端同步网络包和 `.fbx -> .mmesh` 离线转换工具，而不是把复杂二进制 FBX 解析器塞进运行时。
