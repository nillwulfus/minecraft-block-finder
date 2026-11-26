package com.blockfinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.blockfinder.effects.EffectsConfig;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Анимации смерти игрока и мобов. */
public final class DeathEffects {
	public static final String[] STYLES = { "Выкл", "Взрыв кубов", "Вознесение", "Световой столб", "Пепел", "Спираль", "Исчезновение" };

	private record Fx(double x, double y, double z, float yaw, float height, long born, int style, int rgb, long dur, long seed) {
	}

	private static final List<Fx> FX = new ArrayList<>();
	private static final Set<Integer> SEEN = new HashSet<>();
	private static boolean wasDead = false;

	private DeathEffects() {
	}

	public static void tick(Minecraft mc) {
		EffectsConfig cfg = EffectsConfig.get();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			FX.clear();
			SEEN.clear();
			wasDead = false;
			return;
		}
		long now = System.currentTimeMillis();
		FX.removeIf(f -> now - f.born() > f.dur());

		boolean dead = p.isDeadOrDying() || p.getHealth() <= 0f;
		if (dead && !wasDead) {
			if (cfg.deathStyle > 0) {
				spawn(p, now, cfg);
			}
			if (cfg.deathWaypoint) {
				Waypoints.add(mc, "Смерть", Math.floor(p.getX()) + 0.5, Math.floor(p.getY()), Math.floor(p.getZ()) + 0.5,
						"minecraft:skeleton_skull", "#FF5555", true);
			}
		}
		wasDead = dead;

		if (cfg.deathStyle > 0 && cfg.deathMobs) {
			Set<Integer> alive = new HashSet<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof LivingEntity le && e != p) {
					if (le.isDeadOrDying() || le.getHealth() <= 0f) {
						if (SEEN.add(le.getId())) {
							spawn(le, now, cfg);
						}
						alive.add(le.getId());
					}
				}
			}
			SEEN.retainAll(alive);
		}
		while (FX.size() > 40) {
			FX.remove(0);
		}
	}

	private static void spawn(LivingEntity e, long now, EffectsConfig cfg) {
		FX.add(new Fx(e.getX(), e.getY(), e.getZ(), e.yBodyRot, e.getBbHeight(), now, cfg.deathStyle,
				EffectsConfig.rgb(cfg.deathColor), cfg.deathTenths * 100L, now ^ e.getId()));
	}

	public static void preview(Minecraft mc) {
		EffectsConfig cfg = EffectsConfig.get();
		LocalPlayer p = mc.player;
		if (p != null && cfg.deathStyle > 0) {
			spawn(p, System.currentTimeMillis(), cfg);
		}
	}

	public static void render(PoseStack matrices, Vec3 camera) {
		if (FX.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		BlockFinderRenderer.begin(BlockFinderRenderer.WITH_DEPTH);
		BufferBuilder buf = BlockFinderRenderer.buffer();
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		for (Fx f : FX) {
			float t = (now - f.born()) / (float) f.dur();
			if (t < 0f || t > 1f) {
				continue;
			}
			draw(matrices, buf, f, t);
		}
		matrices.popPose();
		BlockFinderRenderer.finish(BlockFinderRenderer.WITH_DEPTH);
	}

	private static void draw(PoseStack m, BufferBuilder buf, Fx f, float t) {
		Random rnd = new Random(f.seed());
		float fade = 1f - t;
		float h = f.height();
		float cx = (float) f.x();
		float cy = (float) f.y();
		float cz = (float) f.z();
		float sec = t * f.dur() / 1000f;
		switch (f.style()) {
			case 1 -> { // взрыв кубов
				for (int i = 0; i < 14; i++) {
					float vx = (rnd.nextFloat() - 0.5f) * 4f;
					float vy = 2f + rnd.nextFloat() * 3f;
					float vz = (rnd.nextFloat() - 0.5f) * 4f;
					float s = 0.06f + rnd.nextFloat() * 0.08f;
					float y = h * 0.5f + vy * sec - 4.9f * sec * sec;
					if (y < 0.05f) {
						y = 0.05f;
					}
					BlockFinderRenderer.cube(m, buf, cx + vx * sec * 0.6f, cy + y, cz + vz * sec * 0.6f, s, s, s, f.rgb(), fade);
				}
			}
			case 2 -> { // вознесение
				m.pushPose();
				m.translate(cx, cy + t * 3f, cz);
				m.mulPose(Axis.YP.rotationDegrees(180f - f.yaw() + t * 180f));
				float r = ((f.rgb() >> 16) & 0xFF) / 255f;
				float g = ((f.rgb() >> 8) & 0xFF) / 255f;
				float b = (f.rgb() & 0xFF) / 255f;
				// Используем простой куб вместо силуэта
				BlockFinderRenderer.cube(m, buf, 0f, h * 0.5f, 0f, 0.3f, h * 0.5f, 0.15f, f.rgb(), 0.7f * fade);
				m.popPose();
				float ringY = cy + h + t * 3f + 0.2f;
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI / 6;
					BlockFinderRenderer.cube(m, buf, cx + (float) Math.cos(a) * 0.35f, ringY, cz + (float) Math.sin(a) * 0.35f,
							0.04f, 0.03f, 0.04f, 0xFFF2A8, fade);
				}
			}
			case 3 -> { // световой столб
				float grow = Math.min(1f, t * 4f);
				BlockFinderRenderer.cube(m, buf, cx, cy + 20f * grow, cz, 0.18f * fade + 0.03f, 20f * grow, 0.18f * fade + 0.03f, f.rgb(), 0.55f * fade);
				float rad = t * 2.5f;
				for (int i = 0; i < 24; i++) {
					double a = i * Math.PI / 12;
					BlockFinderRenderer.cube(m, buf, cx + (float) Math.cos(a) * rad, cy + 0.05f, cz + (float) Math.sin(a) * rad,
							0.06f, 0.03f, 0.06f, f.rgb(), fade);
				}
			}
			case 4 -> { // пепел
				for (int i = 0; i < 50; i++) {
					float ox = (rnd.nextFloat() - 0.5f) * 0.8f;
					float oz = (rnd.nextFloat() - 0.5f) * 0.8f;
					float oy = rnd.nextFloat() * h;
					float sp = 0.4f + rnd.nextFloat() * 0.8f;
					float drift = (float) Math.sin(sec * 2 + i) * 0.15f;
					float s = 0.025f + rnd.nextFloat() * 0.03f;
					BlockFinderRenderer.cube(m, buf, cx + ox + drift, cy + oy + sp * sec, cz + oz, s, s, s, f.rgb(), fade * 0.9f);
				}
			}
			case 5 -> { // спираль
				for (int i = 0; i < 28; i++) {
					float p = i / 28f;
					double a = p * Math.PI * 6 + t * 8;
					float rad = 0.3f + p * 0.5f + t;
					float s = 0.05f;
					BlockFinderRenderer.cube(m, buf, cx + (float) Math.cos(a) * rad, cy + p * (h + 1f) + t * 1.5f,
							cz + (float) Math.sin(a) * rad, s, s, s, f.rgb(), fade);
				}
			}
			case 6 -> { // исчезновение: силуэт сжимается и вращается
				m.pushPose();
				m.translate(cx, cy, cz);
				m.mulPose(Axis.YP.rotationDegrees(180f - f.yaw() + t * 540f));
				float sc = Math.max(0.02f, 1f - t);
				m.scale(sc, sc, sc);
				// Простой куб вместо силуэта
				BlockFinderRenderer.cube(m, buf, 0f, h * 0.5f, 0f, 0.3f, h * 0.5f, 0.15f, f.rgb(), 0.8f * fade);
				m.popPose();
			}
			default -> {
			}
		}
	}
}
