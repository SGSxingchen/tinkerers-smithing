# NeoForge 1.20.1 铁砧兼容性 CI

该目录中的 `ci-probe` 是仅用于 GitHub Hosted Runner 的 Fabric 测试模组，不纳入主模组发布 jar。

它在 dedicated server 已完成资源重载后，模拟旧版持久化配置遗漏 `minecraft:iron_ingot` substitution 的升级情形，再重载配方并记录：unit cost / runtime repair recipe、铁剑 repair recipe 的实际类、Mixin 注入留下的成员，以及材料修理、同物品合并和改名的独立结果。

首轮工作流要求该旧配置升级后仍保留发布版本的默认 iron substitution；未修复时它会产生红测。修复后同一场景应通过铁、钻石、错误材料和下界合金材料规则测试。

根因与修复：旧版生成的持久化 `ingredientSubstitutions` map 会覆盖后来新增的默认 alias。NeoForge 1.20.1 会把铁剑合成材料表示为 `forge:ingots/iron`；旧 map 缺少 iron 时，unit cost 推导失败，服务端没有 `tinkerers_smithing:repair/iron_sword/iron_ingot`，而铁砧 Mixin 的严格白名单会拒绝原版材料修理。生产代码以不可变内置 defaults 为基底，再以用户配置同名项覆盖；没有回退到原版 `canRepair`，因此下界合金仍只允许钻石修理。
