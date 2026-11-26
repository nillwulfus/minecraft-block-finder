package com.blockfinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ищет нужные блоки вокруг игрока среди загруженных клиентом чанков.
 * Секции чанков, в которых нужных блоков точно нет, пропускаются целиком.
 */
public final class BlockScanner {
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final int HARD_LIMIT = 20000;

	private static volatile List<BlockPos> results = List.of();
	private static int cooldown = 0;

	private BlockScanner() {
	}

	public static List<BlockPos> results() {
		return results;
	}

	public static void forceRescan() {
		cooldown = 0;
	}

	public static void tick(Minecraft mc) {
		BlockFinderConfig cfg = BlockFinderClient.config();
		if (!cfg.enabled || mc.level == null || mc.player == null) {
			results = List.of();
			cooldown = 0;
			return;
		}
		if (--cooldown > 0) {
			return;
		}
		cooldown = SCAN_INTERVAL_TICKS;
		scan(mc, cfg);
	}

	private static void scan(Minecraft mc, BlockFinderConfig cfg) {
		Set<Block> targets = cfg.targetBlocks();
		if (targets.isEmpty()) {
			results = List.of();
			return;
		}

		ClientLevel level = mc.level;
		BlockPos origin = mc.player.blockPosition();
		int r = cfg.radius;
		long r2 = (long) r * r;
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		int minCX = (ox - r) >> 4;
		int maxCX = (ox + r) >> 4;
		int minCZ = (oz - r) >> 4;
		int maxCZ = (oz + r) >> 4;

		List<BlockPos> found = new ArrayList<>();

		outer:
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				int minSectionY = chunk.getMinSectionY();

				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section == null || section.hasOnlyAir()) {
						continue;
					}
					int baseY = (minSectionY + i) << 4;
					if (baseY + 15 < oy - r || baseY > oy + r) {
						continue;
					}
					if (!section.maybeHas(state -> targets.contains(state.getBlock()))) {
						continue;
					}

					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState state = section.getBlockState(x, y, z);
								if (!targets.contains(state.getBlock())) {
									continue;
								}
								int wx = (cx << 4) + x;
								int wy = baseY + y;
								int wz = (cz << 4) + z;
								long dx = wx - ox;
								long dy = wy - oy;
								long dz = wz - oz;
								if (dx * dx + dy * dy + dz * dz > r2) {
									continue;
								}
								found.add(new BlockPos(wx, wy, wz));
								if (found.size() >= HARD_LIMIT) {
									break outer;
								}
							}
						}
					}
				}
			}
		}

		found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
		if (found.size() > cfg.maxBlocks) {
			found = new ArrayList<>(found.subList(0, cfg.maxBlocks));
		}
		results = List.copyOf(found);
	}
}
