package folk.sisby.tinkerers_smithing;

import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.values.ValueMap;
import net.minecraft.recipe.Ingredient;

import java.util.HashMap;
import java.util.Map;

public class TinkerersSmithingConfig extends WrappedConfig {
	/**
	 * 配置文件会保留早期生成的 map，新增默认项不会自动写回旧文件。
	 * 此表保证旧配置仍继承发布版本的基础 Forge 兼容 alias；用户同名项可覆盖它。
	 */
	private static final Map<String, String> BUILTIN_INGREDIENT_SUBSTITUTIONS = Map.ofEntries(
		Map.entry("minecraft:gold_ingot", "forge:ingots/gold"),
		Map.entry("minecraft:gold_nugget", "forge:nuggets/gold"),
		Map.entry("minecraft:iron_ingot", "forge:ingots/iron"),
		Map.entry("minecraft:iron_nugget", "forge:nuggets/iron"),
		Map.entry("minecraft:netherite_ingot", "forge:ingots/netherite"),
		Map.entry("minecraft:copper_ingot", "forge:ingots/copper"),
		Map.entry("minecraft:amethyst_shard", "forge:gems/amethyst"),
		Map.entry("minecraft:diamond", "forge:gems/diamond"),
		Map.entry("minecraft:emerald", "forge:gems/emerald"),
		Map.entry("minecraft:chest", "forge:chests/wooden"),
		Map.entry("minecraft:cobblestone", "forge:cobblestone/normal"),
		Map.entry("minecraft:cobbled_deepslate", "forge:cobblestone/deepslate"),
		Map.entry("minecraft:string", "forge:string")
	);

	@Comment("Maps items to equivalent tags that other mods substitute in crafting recipes")
	public final Map<String, String> ingredientSubstitutions = ValueMap.builder("")
		.put("minecraft:gold_ingot", "forge:ingots/gold")
		.put("minecraft:gold_nugget", "forge:nuggets/gold")
		.put("minecraft:iron_ingot", "forge:ingots/iron")
		.put("minecraft:iron_nugget", "forge:nuggets/iron")
		.put("minecraft:netherite_ingot", "forge:ingots/netherite")
		.put("minecraft:copper_ingot", "forge:ingots/copper")
		.put("minecraft:amethyst_shard", "forge:gems/amethyst")
		.put("minecraft:diamond", "forge:gems/diamond")
		.put("minecraft:emerald", "forge:gems/emerald")
		.put("minecraft:chest", "forge:chests/wooden")
		.put("minecraft:cobblestone", "forge:cobblestone/normal")
		.put("minecraft:cobbled_deepslate", "forge:cobblestone/deepslate")
		.put("minecraft:string", "forge:string")
		.build();

	public final boolean matchesOrEquivalent(Ingredient fromRepair, Ingredient fromCrafting) {
		String transformedJsonString = fromRepair.toJson().toString();
		if (transformedJsonString.equals(fromCrafting.toJson().toString())) return true;
		Map<String, String> substitutions = new HashMap<>(BUILTIN_INGREDIENT_SUBSTITUTIONS);
		substitutions.putAll(ingredientSubstitutions);
		for (Map.Entry<String, String> substitution : substitutions.entrySet()) {
			transformedJsonString = transformedJsonString.replace("{\"item\":\"%s\"}".formatted(substitution.getKey()), "{\"tag\":\"%s\"}".formatted(substitution.getValue()));
		}
		return transformedJsonString.equals(fromCrafting.toJson().toString());
	}
}
