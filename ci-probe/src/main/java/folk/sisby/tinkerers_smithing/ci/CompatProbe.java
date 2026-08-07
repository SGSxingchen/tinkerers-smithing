package folk.sisby.tinkerers_smithing.ci;

import com.mojang.authlib.GameProfile;
import folk.sisby.tinkerers_smithing.TinkerersSmithing;
import folk.sisby.tinkerers_smithing.TinkerersSmithingLoader;
import folk.sisby.tinkerers_smithing.recipe.ShapelessRepairRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 只由 CI server 启动的探针。它先模拟旧配置遗漏 iron substitution，重载后直接驱动铁砧
 * ScreenHandler；这能把「动态修理配方缺失」和客户端显示问题明确区分开。
 */
public final class CompatProbe implements ModInitializer {
	private static final Identifier IRON_SWORD_REPAIR = new Identifier("tinkerers_smithing", "repair/iron_sword/iron_ingot");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> beginLegacyConfigReproduction(server)));
	}

	private static void beginLegacyConfigReproduction(MinecraftServer server) {
		Map<String, String> substitutions = TinkerersSmithing.CONFIG.ingredientSubstitutions;
		String removed = substitutions.remove("minecraft:iron_ingot");
		if (removed == null) {
			finish(server, false, "配置中原本没有 minecraft:iron_ingot，无法建立可控旧配置复现", null);
			return;
		}
		TinkerersSmithing.LOGGER.info("TS_COMPAT legacy-config: removed minecraft:iron_ingot -> {} before reload", removed);
		server.reloadResources(server.getDataPackManager().getEnabledNames()).whenComplete((unused, throwable) -> server.execute(() -> {
			if (throwable != null) {
				finish(server, false, "旧配置重载失败", throwable);
				return;
			}
			runAssertions(server);
		}));
	}

	private static void runAssertions(MinecraftServer server) {
		try {
			RecipeManager manager = server.getRecipeManager();
			List<CraftingRecipe> crafting = manager.listAllOfType(RecipeType.CRAFTING);
			long runtimeRepairRecipes = crafting.stream().filter(ShapelessRepairRecipe.class::isInstance).count();
			Recipe<?> ironRecipe = manager.get(IRON_SWORD_REPAIR).orElse(null);
			boolean recipePresent = ironRecipe instanceof ShapelessRepairRecipe;
			String recipeClass = ironRecipe == null ? "<missing>" : ironRecipe.getClass().getName();

			ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), "ts_ci_probe"));
			player.experienceLevel = 100;
			boolean ironMaterialRepair = repairSucceeds(player, Items.IRON_SWORD, Items.IRON_INGOT);
			boolean ironWrongMaterial = repairSucceeds(player, Items.IRON_SWORD, Items.DIAMOND);
			boolean diamondMaterialRepair = repairSucceeds(player, Items.DIAMOND_AXE, Items.DIAMOND);
			boolean netheriteDiamondRepair = repairSucceeds(player, Items.NETHERITE_SWORD, Items.DIAMOND);
			boolean netheriteIngotRepair = repairSucceeds(player, Items.NETHERITE_SWORD, Items.NETHERITE_INGOT);
			boolean ironSameItemCombine = combineSucceeds(player, Items.IRON_SWORD);
			boolean rename = renameSucceeds(player, Items.IRON_SWORD);

			TinkerersSmithing.LOGGER.info("TS_COMPAT recipes={} runtimeRepairRecipes={} ironRecipePresent={} ironRecipeClass={} recipeMixinFields={} anvilMixinMethods={}",
				crafting.size(), runtimeRepairRecipes, recipePresent, recipeClass,
				containsTinkerersMember(manager.getClass().getDeclaredFields()),
				containsTinkerersMember(AnvilScreenHandler.class.getDeclaredMethods()));
			TinkerersSmithing.LOGGER.info("TS_COMPAT material iron_sword+iron_ingot={} iron_sword+diamond={} diamond_axe+diamond={} netherite_sword+diamond={} netherite_sword+netherite_ingot={} same_item_combine={} rename={}",
				ironMaterialRepair, ironWrongMaterial, diamondMaterialRepair, netheriteDiamondRepair, netheriteIngotRepair, ironSameItemCombine, rename);

			// 旧配置也必须继承发布版本的默认 alias；未修复时此处是精确的红测。
			boolean passed = recipePresent && ironMaterialRepair && !ironWrongMaterial && diamondMaterialRepair
				&& netheriteDiamondRepair && !netheriteIngotRepair && ironSameItemCombine && rename;
			finish(server, passed, "旧配置遗漏 iron substitution 后未保留既有材料修理规则", null);
		} catch (Throwable throwable) {
			finish(server, false, "探针执行异常", throwable);
		}
	}

	private static boolean repairSucceeds(ServerPlayerEntity player, Item baseItem, Item addition) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		ItemStack base = new ItemStack(baseItem);
		base.setDamage(Math.max(1, base.getMaxDamage() / 2));
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(base);
		anvil.getSlot(AnvilScreenHandler.INPUT_2_ID).setStack(new ItemStack(addition));
		anvil.updateResult();
		return !anvil.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack().isEmpty() && anvil.canTake(player);
	}

	private static boolean combineSucceeds(ServerPlayerEntity player, Item item) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		ItemStack base = new ItemStack(item);
		base.setDamage(Math.max(1, base.getMaxDamage() / 2));
		ItemStack addition = new ItemStack(item);
		addition.setDamage(Math.max(1, addition.getMaxDamage() / 3));
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(base);
		anvil.getSlot(AnvilScreenHandler.INPUT_2_ID).setStack(addition);
		anvil.updateResult();
		return !anvil.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack().isEmpty() && anvil.canTake(player);
	}

	private static boolean renameSucceeds(ServerPlayerEntity player, Item item) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(new ItemStack(item));
		anvil.setNewItemName("CI");
		anvil.updateResult();
		return !anvil.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack().isEmpty() && anvil.canTake(player);
	}

	private static boolean containsTinkerersMember(java.lang.reflect.Member[] members) {
		for (java.lang.reflect.Member member : members) {
			if (member.getName().contains("tinkerersSmithing")) return true;
		}
		return false;
	}

	private static void finish(MinecraftServer server, boolean passed, String failure, Throwable throwable) {
		String message = throwable == null ? failure : failure + ": " + throwable;
		TinkerersSmithing.LOGGER.info("TS_COMPAT_RESULT status={} message={}", passed ? "PASS" : "FAIL", message);
		try {
			Files.writeString(Path.of("compat-result.json"), "{\"status\":\"" + (passed ? "PASS" : "FAIL") + "\",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}\n");
		} catch (IOException ioException) {
			TinkerersSmithing.LOGGER.error("TS_COMPAT_RESULT 无法写入结果文件", ioException);
		}
		server.stop(false);
	}

	private static final class ProbeAnvil extends AnvilScreenHandler {
		private ProbeAnvil(ServerPlayerEntity player) {
			super(0, player.getInventory());
		}

		private boolean canTake(PlayerEntity player) {
			return super.canTakeOutput(player, true);
		}
	}
}
