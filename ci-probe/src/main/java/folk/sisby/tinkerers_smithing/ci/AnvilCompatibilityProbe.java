package folk.sisby.tinkerers_smithing.ci;

import com.mojang.authlib.GameProfile;
import folk.sisby.tinkerers_smithing.TinkerersSmithing;
import folk.sisby.tinkerers_smithing.recipe.ShapelessRepairRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 只由 Hosted Runner 的真实 dedicated server 启动的铁砧探针。
 *
 * <p>所有断言都通过真实 {@link AnvilScreenHandler} 放入输入槽并调用实际
 * {@code updateResult()} 完成，避免把“配方存在”误当成铁砧兼容。</p>
 */
public final class AnvilCompatibilityProbe implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> run(server)));
	}

	private static void run(MinecraftServer server) {
		try {
			ServerPlayerEntity player = new ServerPlayerEntity(
				server,
				server.getOverworld(),
				new GameProfile(UUID.randomUUID(), "ts_ci_probe"),
				SyncedClientOptions.createDefault()
			);
			List<RecipeEntry<CraftingRecipe>> crafting = player.getWorld().getRecipeManager().listAllOfType(RecipeType.CRAFTING);
			long runtimeRepairRecipes = crafting.stream().filter(entry -> entry.value() instanceof ShapelessRepairRecipe).count();
			boolean ironRepairRecipe = crafting.stream().anyMatch(entry -> entry.value() instanceof ShapelessRepairRecipe repair
				&& repair.baseItem == Items.IRON_SWORD && repair.addition.test(new ItemStack(Items.IRON_INGOT)));

			// 材料修理应在零经验等级下仍可取走；这同时验证输出、伤害和免费修理路径。
			player.experienceLevel = 0;
			Outcome ironSword = repair(player, Items.IRON_SWORD, Items.IRON_INGOT);
			Outcome diamondAxe = repair(player, Items.DIAMOND_AXE, Items.DIAMOND);
			Outcome diamondHelmet = repair(player, Items.DIAMOND_HELMET, Items.DIAMOND);
			Outcome wrongMaterial = repair(player, Items.IRON_SWORD, Items.DIAMOND);
			Outcome netheriteDiamond = repair(player, Items.NETHERITE_SWORD, Items.DIAMOND);
			Outcome netheriteIngot = repair(player, Items.NETHERITE_SWORD, Items.NETHERITE_INGOT);

			// 合并和改名是原有铁砧路径，使用充足经验确认修复没有破坏它们。
			player.experienceLevel = 100;
			Outcome sameItemCombine = combine(player, Items.IRON_SWORD);
			Outcome rename = rename(player, Items.IRON_SWORD);

			boolean passed = ironSword.isFreeRepair() && diamondAxe.isFreeRepair() && diamondHelmet.isFreeRepair()
				&& wrongMaterial.isRejected() && netheriteDiamond.isFreeRepair() && netheriteIngot.isRejected()
				&& sameItemCombine.isRepair() && rename.isRename();
			String detail = "runtime_repair_recipes=" + runtimeRepairRecipes + ", iron_repair_recipe=" + ironRepairRecipe
				+ ", iron_sword+iron_ingot=" + ironSword
				+ ", diamond_axe+diamond=" + diamondAxe
				+ ", diamond_helmet+diamond=" + diamondHelmet
				+ ", iron_sword+diamond=" + wrongMaterial
				+ ", netherite_sword+diamond=" + netheriteDiamond
				+ ", netherite_sword+netherite_ingot=" + netheriteIngot
				+ ", same_item_combine=" + sameItemCombine
				+ ", rename=" + rename;
			finish(server, passed, detail, null);
		} catch (Throwable throwable) {
			finish(server, false, "探针执行异常", throwable);
		}
	}

	private static Outcome repair(ServerPlayerEntity player, Item baseItem, Item addition) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		ItemStack base = damaged(baseItem, 2);
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(base);
		anvil.getSlot(AnvilScreenHandler.INPUT_2_ID).setStack(new ItemStack(addition));
		anvil.updateResult();
		return anvil.outcome(base.getDamage(), false);
	}

	private static Outcome combine(ServerPlayerEntity player, Item item) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		ItemStack base = damaged(item, 2);
		ItemStack addition = damaged(item, 3);
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(base);
		anvil.getSlot(AnvilScreenHandler.INPUT_2_ID).setStack(addition);
		anvil.updateResult();
		return anvil.outcome(base.getDamage(), false);
	}

	private static Outcome rename(ServerPlayerEntity player, Item item) {
		ProbeAnvil anvil = new ProbeAnvil(player);
		anvil.getSlot(AnvilScreenHandler.INPUT_1_ID).setStack(new ItemStack(item));
		anvil.setNewItemName("CI-铁砧");
		anvil.updateResult();
		return anvil.outcome(0, true);
	}

	private static ItemStack damaged(Item item, int divisor) {
		ItemStack stack = new ItemStack(item);
		stack.setDamage(Math.max(1, stack.getMaxDamage() / divisor));
		return stack;
	}

	private static void finish(MinecraftServer server, boolean passed, String detail, Throwable throwable) {
		String message = throwable == null ? detail : detail + ": " + throwable;
		TinkerersSmithing.LOGGER.info("TS_ANVIL_1211_RESULT status={} detail={}", passed ? "PASS" : "FAIL", message);
		String json = "{\"status\":\"" + (passed ? "PASS" : "FAIL") + "\",\"detail\":\"" + escape(message) + "\"}\n";
		try {
			Files.writeString(Path.of("compat-result.json"), json);
		} catch (IOException ioException) {
			TinkerersSmithing.LOGGER.error("TS_ANVIL_1211_RESULT 无法写入结果文件", ioException);
		}
		server.stop(false);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private record Outcome(boolean outputPresent, boolean takeable, int inputDamage, int outputDamage, int levelCost, boolean customName) {
		private boolean isRepair() {
			return outputPresent && takeable && outputDamage < inputDamage;
		}

		private boolean isFreeRepair() {
			return isRepair() && levelCost == 0;
		}

		private boolean isRejected() {
			return !outputPresent && !takeable;
		}

		private boolean isRename() {
			return outputPresent && takeable && customName;
		}
	}

	private static final class ProbeAnvil extends AnvilScreenHandler {
		private ProbeAnvil(ServerPlayerEntity player) {
			super(0, player.getInventory());
		}

		private Outcome outcome(int inputDamage, boolean expectCustomName) {
			ItemStack output = this.getSlot(AnvilScreenHandler.OUTPUT_ID).getStack();
			return new Outcome(
				!output.isEmpty(),
				super.canTakeOutput(this.player, true),
				inputDamage,
				output.isEmpty() ? -1 : output.getDamage(),
				this.getLevelCost(),
				expectCustomName && !output.isEmpty() && output.contains(DataComponentTypes.CUSTOM_NAME)
			);
		}
	}
}
