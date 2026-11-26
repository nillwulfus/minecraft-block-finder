package com.blockfinder.effects;

import java.util.ArrayDeque;
import java.util.Deque;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.blockfinder.BlockFinderRenderer;

/** Эффект силуэтов при беге игрока. */
public final class TrailEffect {
	private record Snap(double x, double y, double z, float yaw, float pitch, long time) {
	}

	private static final Deque<Snap> SNAPS = new ArrayDeque<>();
	private static long lastSnap = 0;

	private TrailEffect() {
	}

	public static void tick(Minecraft mc) {
		EffectsConfig cfg = EffectsConfig.get();
		if (!cfg.trailEnabled) {
			SNAPS.clear();
			return;
		}
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		double speed = p.getDeltaMovement().horizontalDistance();
		if (speed < 0.1 || p.onGround() && !p.isSprinting()) {
			return;
		}
		long now = System.currentTimeMillis();
		long interval = cfg.trailTenths * 10L;
		if (now - lastSnap < interval) {
			return;
		}
		lastSnap = now;
		SNAPS.addLast(new Snap(p.getX(), p.getY(), p.getZ(), p.yBodyRot, p.getXRot(), now));
		while (SNAPS.size() > cfg.trailCount) {
			SNAPS.removeFirst();
		}
	}

	public static void render(PoseStack matrices, Vec3 camera) {
		if (SNAPS.isEmpty()) {
			return;
		}
		EffectsConfig cfg = EffectsConfig.get();
		long now = System.currentTimeMillis();
		long maxAge = cfg.trailTenths * cfg.trailCount * 10L;

		BlockFinderRenderer.begin(BlockFinderRenderer.WITH_DEPTH);
		BufferBuilder buf = BlockFinderRenderer.buffer();

		int rgb = EffectsConfig.rgb(cfg.trailColor);
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float baseAlpha = cfg.trailAlpha / 100f;

		for (Snap s : SNAPS) {
			long age = now - s.time();
			if (age > maxAge) {
				continue;
			}
			float fade = 1f - (age / (float) maxAge);
			matrices.pushPose();
			matrices.translate(s.x() - camera.x, s.y() - camera.y, s.z() - camera.z);
			matrices.mulPose(Axis.YP.rotationDegrees(180f - s.yaw()));
			drawSilhouette(matrices, buf, 0f, r, g, b, baseAlpha * fade, 0f);
			matrices.popPose();
		}

		matrices.popPose();
		BlockFinderRenderer.finish(BlockFinderRenderer.WITH_DEPTH);
	}

	public static void drawSilhouette(PoseStack m, BufferBuilder buf, float yOff, float r, float g, float b, float a, float grow) {
		// Упрощённый силуэт игрока из боксов
		float w = 0.3f + grow;
		float h = 0.9f;
		// Тело
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, -w / 2, yOff, -w / 4, w / 2, yOff + h, w / 4, r, g, b, a);
		// Голова
		float hw = 0.4f + grow;
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, -hw / 2, yOff + h, -hw / 2, hw / 2, yOff + h + hw, hw / 2, r, g, b, a);
		// Руки
		float aw = 0.15f + grow;
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, -w / 2 - aw, yOff + h * 0.6f, -aw / 2, -w / 2, yOff + h * 0.1f, aw / 2, r, g, b, a);
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, w / 2, yOff + h * 0.6f, -aw / 2, w / 2 + aw, yOff + h * 0.1f, aw / 2, r, g, b, a);
		// Ноги
		float lw = 0.15f + grow;
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, -w / 4 - lw / 2, yOff, -lw / 2, -w / 4 + lw / 2, yOff - h * 0.95f, lw / 2, r, g, b, a);
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, w / 4 - lw / 2, yOff, -lw / 2, w / 4 + lw / 2, yOff - h * 0.95f, lw / 2, r, g, b, a);
	}
}
