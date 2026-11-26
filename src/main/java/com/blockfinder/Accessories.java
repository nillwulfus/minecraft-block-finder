package com.blockfinder;

import com.blockfinder.effects.EffectsConfig;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Аксессуары на игроке из цветных боксов. Видны только в виде от третьего лица и только вам (мод клиентский). */
public final class Accessories {
	public enum Type {
		WITCH_HAT("Шляпа ведьмы", 0x4B2A7B, 0xF2B632),
		FOX_EARS("Лисьи уши", 0xE8761E, 0xFFF1DC),
		FOX_TAIL("Лисий хвост", 0xE8761E, 0xFFF1DC),
		CAT_EARS("Кошачьи уши", 0x303038, 0xFF9DB5),
		DEVIL_HORNS("Рожки", 0xC81E1E, 0x5A0A0A),
		HALO("Нимб", 0xFFE066, 0xFFFFFF),
		CROWN("Корона", 0xFFD43B, 0xD62839),
		ANGEL_WINGS("Крылья ангела", 0xFFFFFF, 0xDCEBFF),
		BAT_WINGS("Крылья летучей мыши", 0x3A2255, 0x1A0F28),
		BUTTERFLY_WINGS("Крылья бабочки", 0x4DA3FF, 0xFF8AD8);

		public final String label;
		public final int c1;
		public final int c2;

		Type(String label, int c1, int c2) {
			this.label = label;
			this.c1 = c1;
			this.c2 = c2;
		}
	}

	private Accessories() {
	}

	public static EffectsConfig.Acc get(Type t) {
		EffectsConfig cfg = EffectsConfig.get();
		return cfg.acc.computeIfAbsent(t.name(), k -> {
			EffectsConfig.Acc a = new EffectsConfig.Acc();
			a.c1 = BlockFinderConfig.formatColor(t.c1);
			a.c2 = BlockFinderConfig.formatColor(t.c2);
			return a;
		});
	}

	private static float[] col(int rgb) {
		return new float[] { ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f };
	}

	private static void box(PoseStack m, BufferBuilder buf, float x1, float y1, float z1, float x2, float y2, float z2, float[] c) {
		BlockFinderRenderer.shadedBox(m.last().pose(), buf, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
				Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), c[0], c[1], c[2], 1f);
	}

	public static void render(PoseStack matrices, Vec3 camera) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.getCameraType().isFirstPerson() || p.isInvisible() || p.isSpectator()) {
			return;
		}
		boolean any = false;
		for (Type t : Type.values()) {
			if (get(t).on) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 pos = p.getPosition(pt);
		float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
		float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
		float crouch = p.isCrouching() ? -0.2f : 0f;
		double time = System.currentTimeMillis();

		BlockFinderRenderer.begin(BlockFinderRenderer.WITH_DEPTH);
		BufferBuilder buf = BlockFinderRenderer.buffer();

		matrices.pushPose();
		matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
		matrices.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));

		// ----- крылья и хвост (система координат тела) -----
		for (Type t : Type.values()) {
			EffectsConfig.Acc a = get(t);
			if (!a.on) {
				continue;
			}
			float[] c1 = col(EffectsConfig.rgb(a.c1));
			float[] c2 = col(EffectsConfig.rgb(a.c2));
			float sc = a.scale / 100f;
			switch (t) {
				case FOX_TAIL -> {
					matrices.pushPose();
					matrices.translate(0, 0.78f + crouch, 0.13f);
					matrices.mulPose(Axis.YP.rotationDegrees((float) Math.sin(time / 420.0) * 14f));
					matrices.mulPose(Axis.XP.rotationDegrees(-35f + (float) Math.sin(time / 700.0) * 5f));
					matrices.scale(sc, sc, sc);
					box(matrices, buf, -0.09f, -0.09f, 0f, 0.09f, 0.09f, 0.3f, c1);
					box(matrices, buf, -0.14f, -0.14f, 0.3f, 0.14f, 0.14f, 0.6f, c1);
					box(matrices, buf, -0.11f, -0.11f, 0.6f, 0.11f, 0.11f, 0.82f, c2);
					matrices.popPose();
				}
				case ANGEL_WINGS, BAT_WINGS, BUTTERFLY_WINGS -> {
					float flap = (float) Math.sin(time / 260.0) * 7f;
					for (int s = -1; s <= 1; s += 2) {
						matrices.pushPose();
						matrices.translate(s * 0.12f, 1.33f + crouch, 0.14f);
						matrices.mulPose(Axis.YP.rotationDegrees(-s * (28f + flap)));
						matrices.scale(sc, sc, sc);
						drawWing(matrices, buf, t, s, c1, c2);
						matrices.popPose();
					}
				}
				default -> {
				}
			}
		}

		// ----- голова -----
		matrices.pushPose();
		matrices.translate(0, 1.41f + crouch, 0);
		matrices.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(headYaw - bodyYaw)));
		matrices.mulPose(Axis.XP.rotationDegrees(-pitch));
		for (Type t : Type.values()) {
			EffectsConfig.Acc a = get(t);
			if (!a.on) {
				continue;
			}
			float[] c1 = col(EffectsConfig.rgb(a.c1));
			float[] c2 = col(EffectsConfig.rgb(a.c2));
			float sc = a.scale / 100f;
			matrices.pushPose();
			matrices.translate(0, 0.5f, 0);
			matrices.scale(sc, sc, sc);
			matrices.translate(0, -0.5f, 0);
			drawHead(matrices, buf, t, c1, c2, time);
			matrices.popPose();
		}
		matrices.popPose();

		matrices.popPose();
		BlockFinderRenderer.finish(BlockFinderRenderer.WITH_DEPTH);
	}

	private static void drawHead(PoseStack m, BufferBuilder buf, Type t, float[] c1, float[] c2, double time) {
		switch (t) {
			case WITCH_HAT -> {
				box(m, buf, -0.4f, 0.5f, -0.4f, 0.4f, 0.56f, 0.4f, c1);
				box(m, buf, -0.27f, 0.56f, -0.27f, 0.27f, 0.64f, 0.27f, c2);
				box(m, buf, -0.25f, 0.64f, -0.25f, 0.25f, 0.8f, 0.25f, c1);
				box(m, buf, -0.17f, 0.8f, -0.17f, 0.17f, 1.0f, 0.17f, c1);
				box(m, buf, -0.09f, 1.0f, -0.09f, 0.09f, 1.18f, 0.09f, c1);
				box(m, buf, -0.03f, 1.18f, -0.03f, 0.15f, 1.3f, 0.07f, c1);
			}
			case FOX_EARS -> {
				for (int s = -1; s <= 1; s += 2) {
					float cx = s * 0.14f;
					box(m, buf, cx - 0.08f, 0.5f, 0.0f, cx + 0.08f, 0.8f, 0.12f, c1);
					box(m, buf, cx - 0.045f, 0.5f, -0.012f, cx + 0.045f, 0.68f, 0.0f, c2);
				}
			}
			case CAT_EARS -> {
				for (int s = -1; s <= 1; s += 2) {
					float cx = s * 0.17f;
					box(m, buf, cx - 0.07f, 0.5f, -0.06f, cx + 0.07f, 0.68f, 0.06f, c1);
					box(m, buf, cx - 0.04f, 0.5f, -0.07f, cx + 0.04f, 0.62f, -0.06f, c2);
				}
			}
			case DEVIL_HORNS -> {
				for (int s = -1; s <= 1; s += 2) {
					float cx = s * 0.15f;
					box(m, buf, cx - 0.04f, 0.5f, -0.14f, cx + 0.04f, 0.64f, -0.06f, c1);
					box(m, buf, cx + s * 0.02f - 0.035f, 0.64f, -0.13f, cx + s * 0.02f + 0.035f, 0.78f, -0.06f, c1);
					box(m, buf, cx + s * 0.05f - 0.025f, 0.78f, -0.12f, cx + s * 0.05f + 0.025f, 0.88f, -0.07f, c2);
				}
			}
			case HALO -> {
				float y = 0.78f + (float) Math.sin(time / 500.0) * 0.03f;
				for (int i = 0; i < 16; i++) {
					double a = i * Math.PI / 8;
					float x = (float) Math.cos(a) * 0.32f;
					float z = (float) Math.sin(a) * 0.32f;
					box(m, buf, x - 0.035f, y - 0.02f, z - 0.035f, x + 0.035f, y + 0.02f, z + 0.035f, i % 4 == 0 ? c2 : c1);
				}
			}
			case CROWN -> {
				box(m, buf, -0.27f, 0.5f, -0.27f, 0.27f, 0.6f, 0.27f, c1);
				for (int i = -1; i <= 1; i++) {
					for (int j = -1; j <= 1; j++) {
						if (i == 0 && j == 0) {
							continue;
						}
						float x = i * 0.2f;
						float z = j * 0.2f;
						box(m, buf, x - 0.05f, 0.6f, z - 0.05f, x + 0.05f, 0.72f, z + 0.05f, c1);
					}
				}
				box(m, buf, -0.04f, 0.52f, -0.29f, 0.04f, 0.58f, -0.27f, c2);
			}
			default -> {
			}
		}
	}

	private static void drawWing(PoseStack m, BufferBuilder buf, Type t, int s, float[] c1, float[] c2) {
		switch (t) {
			case ANGEL_WINGS -> {
				for (int i = 0; i < 4; i++) {
					float x1 = 0.04f + 0.2f * i;
					float x2 = x1 + 0.2f;
					float top = 0.3f + 0.12f * i;
					float bottom = -0.4f + 0.12f * i;
					box(m, buf, s * x1, bottom, -0.025f, s * x2, top, 0.025f, i == 3 ? c2 : c1);
					box(m, buf, s * x1, bottom - 0.12f, 0.025f, s * x2, bottom, 0.05f, c2);
				}
			}
			case BAT_WINGS -> {
				box(m, buf, s * 0.04f, -0.03f, -0.02f, s * 0.95f, 0.03f, 0.02f, c2);
				for (int i = 0; i < 4; i++) {
					float x1 = 0.04f + 0.23f * i;
					float x2 = x1 + 0.23f;
					float bottom = -0.55f + 0.12f * i;
					box(m, buf, s * x1, bottom, -0.01f, s * x2, -0.03f, 0.01f, c1);
					box(m, buf, s * (x2 - 0.015f), bottom, -0.02f, s * (x2 + 0.015f), 0f, 0.02f, c2);
				}
			}
			case BUTTERFLY_WINGS -> {
				box(m, buf, s * 0.04f, 0f, -0.02f, s * 0.72f, 0.62f, 0.02f, c1);
				box(m, buf, s * 0.04f, -0.46f, -0.02f, s * 0.52f, 0f, 0.02f, c2);
				box(m, buf, s * 0.22f, 0.2f, 0.02f, s * 0.46f, 0.44f, 0.035f, c2);
				box(m, buf, s * 0.18f, -0.3f, 0.02f, s * 0.36f, -0.12f, 0.035f, c1);
			}
			default -> {
			}
		}
	}
}
