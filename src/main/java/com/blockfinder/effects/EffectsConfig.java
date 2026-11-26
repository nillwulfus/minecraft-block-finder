package com.blockfinder.effects;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Конфигурация визуальных эффектов, анимаций и аксессуаров. */
public final class EffectsConfig {
	public static class Acc {
		public boolean on = false;
		public String c1 = "#FFFFFF";
		public String c2 = "#FFFFFF";
		public int scale = 100;
	}

	// Анимации смерти
	public int deathStyle = 1;
	public String deathColor = "#FF5555";
	public int deathTenths = 15;
	public boolean deathMobs = true;
	public boolean deathWaypoint = true;

	// Силуэты при беге
	public boolean trailEnabled = true;
	public int trailCount = 8;
	public int trailTenths = 8;
	public String trailColor = "#1A1A22";
	public int trailAlpha = 70;

	// Тотем бессмертия
	public boolean totemShow = true;
	public int totemX = 10;
	public int totemY = 10;
	public String totemColor = "#FFD700";
	public boolean totemFlash = true;
	public boolean totemSound = true;
	public boolean totemWarn = true;

	// Щит
	public boolean shieldShow = true;
	public int shieldX = 10;
	public int shieldY = 50;
	public String shieldColor = "#4DA6FF";
	public boolean shieldGlow = true;
	public int shieldGlowAlpha = 30;

	// Аксессуары
	public Map<String, Acc> acc = new HashMap<>();

	private static EffectsConfig instance;
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockfinder-effects.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static EffectsConfig get() {
		if (instance == null) {
			try {
				if (Files.exists(FILE)) {
					instance = GSON.fromJson(Files.readString(FILE), EffectsConfig.class);
				}
			} catch (Exception e) {
				System.err.println("[BlockFinder] Ошибка чтения effects config: " + e);
			}
			if (instance == null) {
				instance = new EffectsConfig();
			}
		}
		return instance;
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(get()));
		} catch (IOException e) {
			System.err.println("[BlockFinder] Ошибка сохранения effects config: " + e);
		}
	}

	public static int rgb(String hex) {
		try {
			if (hex.startsWith("#")) {
				hex = hex.substring(1);
			}
			return Integer.parseInt(hex, 16);
		} catch (Exception e) {
			return 0xFFFFFF;
		}
	}
}
