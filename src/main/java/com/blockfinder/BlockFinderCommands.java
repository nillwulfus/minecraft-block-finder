package com.blockfinder;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Команда /bf (она же /blockfinder). */
public final class BlockFinderCommands {
	private static final SuggestionProvider<FabricClientCommandSource> BLOCK_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::toString), builder);

	private BlockFinderCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ctx) {
		var root = ClientCommandManager.literal("bf")
				.executes(c -> {
					BlockFinderClient.openMenuNextTick();
					return 1;
				})
				.then(ClientCommandManager.literal("menu").executes(c -> {
					BlockFinderClient.openMenuNextTick();
					return 1;
				}))
				.then(ClientCommandManager.literal("toggle").executes(c -> {
					BlockFinderConfig cfg = BlockFinderClient.config();
					cfg.enabled = !cfg.enabled;
					saveAndRescan(cfg);
					msg(c.getSource(), "Подсветка: " + (cfg.enabled ? "включена" : "выключена"));
					return 1;
				}))
				.then(ClientCommandManager.literal("through")
						.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(c -> {
							BlockFinderConfig cfg = BlockFinderClient.config();
							cfg.throughWalls = BoolArgumentType.getBool(c, "value");
							saveAndRescan(cfg);
							msg(c.getSource(), "Подсветка сквозь блоки: " + (cfg.throughWalls ? "вкл" : "выкл"));
							return 1;
						})))
				.then(ClientCommandManager.literal("hud")
						.then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes(c -> {
							BlockFinderConfig cfg = BlockFinderClient.config();
							cfg.showHud = BoolArgumentType.getBool(c, "value");
							saveAndRescan(cfg);
							msg(c.getSource(), "Координаты на экране: " + (cfg.showHud ? "вкл" : "выкл"));
							return 1;
						})))
				.then(ClientCommandManager.literal("set")
						.then(ClientCommandManager.argument("block", StringArgumentType.greedyString())
								.suggests(BLOCK_SUGGESTIONS)
								.executes(c -> {
									String id = BlockFinderConfig.normalizeBlockId(StringArgumentType.getString(c, "block"));
									if (id == null) {
										msg(c.getSource(), "Нет такого блока.");
										return 0;
									}
									BlockFinderConfig cfg = BlockFinderClient.config();
									cfg.blocks.clear();
									cfg.blocks.add(id);
									cfg.enabled = true;
									saveAndRescan(cfg);
									msg(c.getSource(), "Ищу: " + id);
									return 1;
								})))
				.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("block", StringArgumentType.greedyString())
								.suggests(BLOCK_SUGGESTIONS)
								.executes(c -> {
									String id = BlockFinderConfig.normalizeBlockId(StringArgumentType.getString(c, "block"));
									if (id == null) {
										msg(c.getSource(), "Нет такого блока.");
										return 0;
									}
									BlockFinderConfig cfg = BlockFinderClient.config();
									if (!cfg.blocks.contains(id)) {
										cfg.blocks.add(id);
									}
									cfg.enabled = true;
									saveAndRescan(cfg);
									msg(c.getSource(), "Добавлен: " + id + " (всего: " + cfg.blocks.size() + ")");
									return 1;
								})))
				.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("block", StringArgumentType.greedyString())
								.suggests(BLOCK_SUGGESTIONS)
								.executes(c -> {
									String id = BlockFinderConfig.normalizeBlockId(StringArgumentType.getString(c, "block"));
									BlockFinderConfig cfg = BlockFinderClient.config();
									if (id == null || !cfg.blocks.remove(id)) {
										msg(c.getSource(), "Этого блока нет в списке поиска.");
										return 0;
									}
									saveAndRescan(cfg);
									msg(c.getSource(), "Убран: " + id);
									return 1;
								})))
				.then(ClientCommandManager.literal("clear").executes(c -> {
					BlockFinderConfig cfg = BlockFinderClient.config();
					cfg.blocks.clear();
					saveAndRescan(cfg);
					msg(c.getSource(), "Список поиска очищен.");
					return 1;
				}))
				.then(ClientCommandManager.literal("radius")
						.then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(4, BlockFinderConfig.MAX_RADIUS))
								.executes(c -> {
									BlockFinderConfig cfg = BlockFinderClient.config();
									cfg.radius = IntegerArgumentType.getInteger(c, "blocks");
									saveAndRescan(cfg);
									msg(c.getSource(), "Радиус поиска: " + cfg.radius);
									return 1;
								})))
				.then(ClientCommandManager.literal("opacity")
						.then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(5, 100))
								.executes(c -> {
									BlockFinderConfig cfg = BlockFinderClient.config();
									cfg.opacity = IntegerArgumentType.getInteger(c, "percent");
									saveAndRescan(cfg);
									msg(c.getSource(), "Непрозрачность: " + cfg.opacity + "%");
									return 1;
								})))
				.then(ClientCommandManager.literal("color")
						.then(ClientCommandManager.argument("hex", StringArgumentType.word()).executes(c -> {
							String hex = StringArgumentType.getString(c, "hex");
							if (BlockFinderConfig.parseColor(hex) < 0) {
								msg(c.getSource(), "Цвет нужен в формате RRGGBB или #RRGGBB, например ff0000.");
								return 0;
							}
							BlockFinderConfig cfg = BlockFinderClient.config();
							cfg.color = hex.startsWith("#") ? hex : "#" + hex;
							saveAndRescan(cfg);
							msg(c.getSource(), "Цвет: " + cfg.color);
							return 1;
						})))
				.then(ClientCommandManager.literal("list").executes(c -> {
					listCoords(c.getSource());
					return 1;
				}));

		var node = dispatcher.register(root);
		dispatcher.register(ClientCommandManager.literal("blockfinder").redirect(node));
	}

	private static void listCoords(FabricClientCommandSource source) {
		Minecraft mc = Minecraft.getInstance();
		List<BlockPos> list = BlockScanner.results();
		if (list.isEmpty() || mc.player == null || mc.level == null) {
			msg(source, "Ничего не найдено в радиусе " + BlockFinderClient.config().radius + " блоков.");
			return;
		}
		BlockPos me = mc.player.blockPosition();
		msg(source, "Найдено: " + list.size() + " (показаны ближайшие 20)");
		int shown = 0;
		for (BlockPos p : list) {
			if (shown++ >= 20) {
				break;
			}
			Block block = mc.level.getBlockState(p).getBlock();
			int dist = (int) Math.round(Math.sqrt(p.distSqr(me)));
			source.sendFeedback(Component.literal(
					block.getName().getString() + "  " + p.getX() + " " + p.getY() + " " + p.getZ() + "  (" + dist + " м)"));
		}
	}

	private static void saveAndRescan(BlockFinderConfig cfg) {
		cfg.save();
		BlockScanner.forceRescan();
	}

	private static void msg(FabricClientCommandSource source, String text) {
		source.sendFeedback(Component.literal("[BlockFinder] " + text));
	}
}
