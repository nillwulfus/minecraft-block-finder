package com.blockfinder;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class BlockFinderClient implements ClientModInitializer {
	public static final String MOD_ID = "blockfinder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static BlockFinderConfig config;
	private static boolean openMenu;

	public static BlockFinderConfig config() {
		return config;
	}

	/** Открыть меню на следующем тике (из команды нельзя открыть экран сразу - его закроет чат). */
	public static void openMenuNextTick() {
		openMenu = true;
	}

	@Override
	public void onInitializeClient() {
		config = BlockFinderConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.blockfinder.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));
		KeyMapping toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.blockfinder.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));
		KeyMapping wallsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.blockfinder.through_walls", InputConstants.Type.KEYSYM, InputConstants.KEY_X, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				openMenu = true;
			}
			while (toggleKey.consumeClick()) {
				config.enabled = !config.enabled;
				config.save();
				BlockScanner.forceRescan();
				status(client, "Подсветка: " + (config.enabled ? "включена" : "выключена"));
			}
			while (wallsKey.consumeClick()) {
				config.throughWalls = !config.throughWalls;
				config.save();
				status(client, "Сквозь блоки: " + (config.throughWalls ? "вкл" : "выкл"));
			}
			if (openMenu) {
				openMenu = false;
				client.setScreen(new BlockFinderScreen(client.screen));
			}
			BlockScanner.tick(client);
		});

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(BlockFinderRenderer::render);

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "coords"), BlockFinderClient::renderHud);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "waypoints"), Waypoints::renderHud);

		ClientCommandRegistrationCallback.EVENT.register(BlockFinderCommands::register);
	}

	private static void status(Minecraft client, String text) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal("[BlockFinder] " + text), true);
		}
	}

	private static void renderHud(GuiGraphics graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!config.enabled || !config.showHud || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		List<BlockScanner.Hit> list = BlockScanner.results();
		int x = 6;
		int y = 6;
		int lineHeight = mc.font.lineHeight + 2;

		if (list.isEmpty()) {
			graphics.drawString(mc.font, "Block Finder: ничего не найдено", x, y, 0xFFAAAAAA);
			return;
		}

		graphics.drawString(mc.font, "Block Finder: найдено " + list.size(), x, y, 0xFFFFFFFF);
		y += lineHeight;

		BlockPos me = mc.player.blockPosition();
		int n = Math.min(config.hudLines, list.size());
		for (int i = 0; i < n; i++) {
			BlockScanner.Hit hit = list.get(i);
			BlockPos p = hit.pos();
			int dist = (int) Math.round(Math.sqrt(p.distSqr(me)));
			String name = mc.level.getBlockState(p).getBlock().getName().getString();
			int color = 0xFF000000 | (hit.rgb() & 0xFFFFFF);
			graphics.drawString(mc.font,
					name + "  " + p.getX() + " " + p.getY() + " " + p.getZ() + "  (" + dist + " м)",
					x, y, color);
			y += lineHeight;
		}
	}
}
