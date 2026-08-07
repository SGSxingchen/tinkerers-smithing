# NeoForge 1.21.1 铁砧兼容性 CI

## 范围与基线

本修复分支以 `sisby-folk/tinkerers-smithing` 上游 `1.21` 的
`6bc2b49c44fa2a7b62386e1ca2c773d2b4f1189f` 为基线；该源码声明版本
`2.7.2+1.21`、兼容 Minecraft `1.21, 1.21.1`。本分支不从既有 1.20.1
修复分支或标签取代码。

`ci-probe` 是仅在 GitHub Hosted Runner dedicated server 启动的 Fabric 测试模组，
不包含在主模组发布 jar 中。它创建真实的 `AnvilScreenHandler`，放入损坏物品和材料，
调用实际 `updateResult()`，然后检查输出是否存在、是否可取、伤害是否真的下降。

探针覆盖：

- 铁剑 + 铁锭；
- 钻石斧 + 钻石、钻石头盔 + 钻石；
- 铁剑 + 错误的钻石材料；
- 下界合金剑 + 钻石成功、+ 下界合金锭拒绝；
- 同物品合并和改名；
- 服务端正常停服、没有 `MixinApplyError`、`InvalidInjectionException`、`FATAL` 或 crash-report。

材料修理使用零经验等级测试，因此“输出可取”同时验证免费修理的真实铁砧路径，
而不是只确认配方存在。

## 固定运行环境

| 矩阵 | 固定版本与来源 |
| --- | --- |
| Fabric 基线 | Minecraft 1.21.1、Fabric Loader 0.15.11、Fabric API 0.116.7+1.21.1、Fabric Installer 1.0.1（Fabric Maven） |
| NeoForge 组合 | NeoForge 21.1.226、Connector 2.0.0-beta.14+1.21.1-full、Forgified Fabric API 0.116.7+2.2.4+1.21.1（NeoForge Maven / Sinytra Releases） |

所有下载项都在脚本中固定 URL 和 SHA-256。NeoForge 组合刻意采用公开问题日志所用的
`21.1.226` / Connector beta.14 / FFAPI 2.2.4 组合，以确保红测与修复都针对客户报告的环境。

## 静态调研结论与证据保存

上游 `AnvilScreenHandlerMixin` 的关键注入均以 Yarn `updateResult` 为目标。公开 issue
曾称 NeoForge 把核心逻辑抽到 `createResultInternal`，但对实际
`neoforge-21.1.226-installer.jar` 的 server patch 与 Connector beta.14 内置映射的静态检查
均没有发现该方法：映射仍将 `AnvilScreenHandler.updateResult` 对应到
`AnvilMenu.createResult`。因此不能仅凭 issue 把目标改到不存在的方法。

NeoForge job 会保存 Connector 实际转换缓存中的主模组 jar、转换后 refmap、`patch_audit.txt`、
变换后 `AnvilScreenHandlerMixin` 与实际导出的 `AnvilMenu` 的 `javap -p -c -v` 输出，作为每次红绿
测试的运行时转换证据。探针也记录运行时修理配方数量与铁剑配方是否存在，但不会把配方存在当作
铁砧成功。脚本严格
断言转换后的 refmap 仍指向 `AnvilMenu.createResult()`，且不包含 `createResultInternal`；生产修复
只会根据这些产物与真实探针失败点决定。

## 根因与最小修复

红测 run [31149603197](https://github.com/SGSxingchen/tinkerers-smithing/actions/runs/31149603197)
在同一次 Hosted Runner 验证中得到 Fabric 通过、NeoForge 失败；NeoForge 服务端正常停服，失败仅来自探针
断言。增强证据 run [31149939607](https://github.com/SGSxingchen/tinkerers-smithing/actions/runs/31149939607)
保存的真实 Connector 转换 jar、refmap 与导出 `AnvilMenu` 证明：目标仍是
`AnvilMenu.createResult()`，并且运行时有 69 个动态修理配方、铁剑 + 铁锭配方存在。

两个平台的导出字节码证明：材料修理的两个 `setDamage` 附近 `@ModifyVariable` 都是有意修改成本
局部变量，而非伤害参数；不能改成 `@ModifyArg`，否则会改变 Fabric 修理量。真正偏移的是
`allowFreeRepairs`：Fabric 把它放在“最终 cost 写入”之后，令零成本修理把局部成本改为 1，从而
保留输出；NeoForge 因为 `Property.set` 的全局 ordinal 增加，把它放在仅处理 40 级限制的
`cost.set(39)` 之后。普通材料修理不会走到那里，局部成本保留为 0，随后的
`if (cost <= 0) result = EMPTY` 清空输出。

生产补丁只改变这一处选择器：从最终成本计算的 `MathHelper.clamp(long, long, long)` 开始切片，选择
切片内第一次 `Property.set`。因此两端都精确命中最终 cost 写入，不再依赖会被 NeoForge 增加的
全局调用 ordinal；规则层、配方、目标方法、修理伤害和其余注入均不改动。

## 已保留的红绿证据

- 红测：[run 31149603197](https://github.com/SGSxingchen/tinkerers-smithing/actions/runs/31149603197)
  （提交 `4190fa7`）中，Fabric 1.21.1 通过而 NeoForge 组合失败；两端都完成正常停服。NeoForge
  探针记录了 69 个运行时修理配方与铁剑配方存在，却在最终输出为空时失败，排除了“没有配方”的假设。
- 绿测：[run 31150604134](https://github.com/SGSxingchen/tinkerers-smithing/actions/runs/31150604134)
  （提交 `2fd4e02`）中 build、Fabric 1.21.1、NeoForge 21.1.226 + Connector beta.14 + FFAPI
  2.2.4 全部成功。两端探针均确认铁剑 + 铁锭、钻石斧和钻石头盔材料修理可取且成本为 0；错误材料
  被拒绝；下界合金剑只接受钻石、不接受下界合金锭；同物品合并与改名正常。

绿测的 NeoForge 转换 jar/refmap 仍明确指向 `AnvilMenu.createResult()`，不含
`createResultInternal`；导出的 `AnvilMenu` 显示 `allowFreeRepairs` 紧跟在最终
`Mth.clamp` 与 `DataSlot.set` 之后。两个诊断日志均无 MixinApplyError、InvalidInjectionException、
FATAL 或 crash-report，并记录了正常停服。

## 发布约束

上游 Gradle 的 `fullRelease/githubRelease` 仍指向 `1.19` 标签，严禁用于此分支。
工作流仅在 `v2.7.2+1.21-neoforge-fix.*` 标签上、且 build、Fabric、NeoForge 三个 job
全部成功后，下载同一 Actions run 的主模组 artifact 并创建 fork Release。标签构建通过
`-PreleaseVersion` 将版本写入 jar 名与模元数据；不会使用本地产物。

最终 Release 的 URL、附件名称、大小及 SHA-256 会在标签工作流全绿后补充到本文件。
