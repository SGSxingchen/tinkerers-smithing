# NeoForge 1.20.1 铁砧兼容性 CI

该目录中的 `ci-probe` 是仅用于 GitHub Hosted Runner 的 Fabric 测试模组，不纳入主模组发布 jar。

它在 dedicated server 已完成资源重载后，模拟旧版持久化配置遗漏 `minecraft:iron_ingot` substitution 的升级情形，再重载配方并记录：unit cost / runtime repair recipe、铁剑 repair recipe 的实际类、Mixin 注入留下的成员，以及材料修理、同物品合并和改名的独立结果。

首轮工作流要求该旧配置升级后仍保留发布版本的默认 iron substitution；未修复时它会产生红测。修复后同一场景应通过铁、钻石、错误材料和下界合金材料规则测试。
