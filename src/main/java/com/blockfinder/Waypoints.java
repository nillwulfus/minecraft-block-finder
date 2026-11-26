package com.blockfinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Метки игрока: имя, иконка-предмет, цвет. Показываются в мире (куб и луч) и на экране. */
public final class Waypoints {
	public static class Waypoint {
		public String id = Long.toHexString(System.nanoTime());
		public String name = "Метка";
		public String dim = "";
		public double x;
		public double y;
		public double z;
		public String icon = "minecraft:compass";
		public String color = "#FFD43B";
		public boolean visible = true;
		public boolean auto = false;

		public ItemStack stack() {
			Identifier id = Identifier.tryParse(icon);
			Item item = id == null ? Items.COMPASS : BuiltInRegistries.ITEM.getValue(id);
			return new ItemStack(item == null ? Items.COMPASS : item);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockfinder-waypoints.json");
	private static List<Waypoint> list = new ArrayList<>();
	private static boolean loaded = false;

	private Waypoints() {
	}

	public static List<Waypoint> all() {
		if (!loaded) {
			loaded = true;
			try {
				if (Files.exists(FILE)) {
					List<Waypoint> l = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Waypoint>>() {
					}.getType());
					if (l != null) {
						list = l;
					}
				}
			} catch (Exception e) {
				System.err.println("[BlockFinder] Не удалось прочитать метки: " + e);
			}
		}
		return list;
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(all()));
		} catch (IOException e) {
			System.err.println("[BlockFinder] Не удалось сохранить метки: " + e);
		}
	}

	public static String dimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().toString();
	}

	public static Waypoint add(Minecraft mc, String name, double x, double y, double z, String icon, String color, boolean auto) {
		Waypoint w = new Waypoint();
		w.name = name;
		w.x = x;
		w.y = y;
		w.z = z;
		w.dim = dimension(mc);
		w.icon = icon;
		w.color = color;
		w.auto = auto;
		all().add(w);
		if (auto) {
			int autos = 0;
			for (int i = all().size() - 1; i >= 0; i--) {
				if (all().get(i).auto && ++autos > 5) {
					all().remove(i);
				}
			}
		}
		save();
		return w;
	}

	public static Waypoint addHere(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return null;
		}
		int n = 1;
		for (Waypoint w : all()) {
			if (!w.auto) {
				n++;
			}
		}
		return add(mc, "Метка " + n, Math.floor(p.getX()) + 0.5, Math.floor(p.getY()), Math.floor(p.getZ()) + 0.5,
				"minecraft:compass", "#FFD43B", false);
	}

	private static boolean here(Minecraft mc, Waypoint w) {
		return w.visible && w.dim.equals(dimension(mc));
	}

	private static int rgb(Waypoint w) {
		return Math.max(0, BlockFinderConfig.parseColor(w.color));
	}

	public static void render(PoseStack matrices, Vec3 camera) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || all().isEmpty()) {
			return;
		}
		boolean any = false;
		for (Waypoint w : all()) {
			if (here(mc, w)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		long now = System.currentTimeMillis();
		BlockFinderRenderer.begin(BlockFinderRenderer.WITH_DEPTH);
		BufferBuilder buf = BlockFinderRenderer.buffer();
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		for (Waypoint w : all()) {
			if (!here(mc, w)) {
				continue;
			}
			int c = rgb(w);
			float bob = (float) Math.sin(now / 500.0) * 0.08f;
			BlockFinderRenderer.cube(matrices, buf, (float) w.x, (float) w.y + 0.9f + bob, (float) w.z, 0.22f, 0.22f, 0.22f, c, 0.9f);
			BlockFinderRenderer.cube(matrices, buf, (float) w.x, (float) w.y + 64f, (float) w.z, 0.04f, 64f, 0.04f, c, 0.35f);
		}
		matrices.popPose();
		BlockFinderRenderer.finish(BlockFinderRenderer.WITH_DEPTH);
	}

	public static void renderHud(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || all().isEmpty()) {
			return;
		}
		float pt = tracker.getGameTimeDeltaPartialTick(false);
		Vec3 eye = p.getEyePosition(pt);
		double yaw = Math.toRadians(p.getViewYRot(pt));
		double pitch = Math.toRadians(p.getViewXRot(pt));
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double tanH = tanV * w / (double) h;

		for (Waypoint wp : all()) {
			if (!here(mc, wp)) {
				continue;
			}
			double dx = wp.x - eye.x;
			double dy = wp.y + 1.0 - eye.y;
			double dz = wp.z - eye.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double df = dx * fx + dy * fy + dz * fz;
			double dr = dx * rx + dz * rz;
			double du = dx * ux + dy * uy + dz * uz;
			double sx;
			double sy;
			boolean edge = false;
			if (df > 0.05) {
				sx = w / 2.0 + dr / df / tanH * w / 2.0;
				sy = h / 2.0 - du / df / tanV * h / 2.0;
			} else {
				sx = w / 2.0 - dr * 1000;
				sy = h / 2.0 + du * 1000;
				edge = true;
			}
			int m = 14;
			if (sx < m || sx > w - m || sy < m + 10 || sy > h - m - 10) {
				edge = true;
				double cx = sx - w / 2.0;
				double cy = sy - h / 2.0;
				double k = Math.min((w / 2.0 - m) / Math.max(1e-6, Math.abs(cx)), (h / 2.0 - m - 10) / Math.max(1e-6, Math.abs(cy)));
				sx = w / 2.0 + cx * k;
				sy = h / 2.0 + cy * k;
			}
			int ix = (int) sx;
			int iy = (int) sy;
			int c = 0xFF000000 | rgb(wp);
			g.fill(ix - 10, iy - 10, ix + 10, iy + 10, 0x90000000);
			g.renderOutline(ix - 10, iy - 10, 20, 20, c);
			g.renderItem(wp.stack(), ix - 8, iy - 8);
			String label = wp.name + " " + (int) dist + " м";
			if (!edge) {
				int tw = mc.font.width(label);
				g.drawString(mc.font, label, ix - tw / 2, iy + 13, c, true);
			}
		}
	}
}
