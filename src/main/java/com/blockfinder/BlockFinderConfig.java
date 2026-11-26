package com.blockfinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.loader.api.FabricLoader;

/** Настройки мода. Хранятся в config/blockfinder.json. */
public class BlockFinderConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blockfinder.json");

	public static final int MAX_RADIUS = 96;
	public static final int MAX_BLOCKS = 1000;

	/** Общий выключатель подсветки. */
	public boolean enabled = true;
	/** Подсвечивать сквозь другие блоки. */
	public boolean throughWalls = true;
	/** Показывать список координат на экране. */
	public boolean showHud = true;
	/** Какие блоки искать (id вида minecraft:diamond_ore). */
	public List<String> blocks = new ArrayList<>(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"));
	/** Радиус поиска в блоках. */
	public int radius = 32;
	/** Максимум подсвечиваемых блоков (ближайшие). */
	public int maxBlocks = 300;
	/** Цвет подсветки в формате #RRGGBB. */
	public String color = "#00FFFF";
	/** Непрозрачность заливки, 5..100 (%). */
	public int opacity = 40;
	/** Сколько ближайших блоков показывать на экране. */
	public int hudLines = 5;
	/** Режим отображения: 0=заливка, 1=контур, 2=оба. */
	public int renderMode = 0;
	/** Звук при нахождении блоков. */
	public boolean soundEnabled = true;
	/** Цвета для отдельных блоков (id -> #RRGGBB). */
	public java.util.Map<String, String> blockColors = new java.util.HashMap<>();

	public static BlockFinderConfig load() {
		BlockFinderConfig cfg = null;
		if (Files.exists(FILE)) {
			try {
				cfg = GSON.fromJson(Files.readString(FILE), BlockFinderConfig.class);
			} catch (Exception e) {
				BlockFinderClient.LOGGER.warn("Не удалось прочитать конфиг, используются значения по умолчанию", e);
			}
		}
		if (cfg == null) {
			cfg = new BlockFinderConfig();
		}
		cfg.sanitize();
		cfg.save();
		return cfg;
	}

	public void save() {
		sanitize();
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			BlockFinderClient.LOGGER.warn("Не удалось сохранить конфиг", e);
		}
	}

	public void sanitize() {
		if (blocks == null) {
			blocks = new ArrayList<>();
		}
		radius = Math.max(4, Math.min(MAX_RADIUS, radius));
		maxBlocks = Math.max(1, Math.min(MAX_BLOCKS, maxBlocks));
		opacity = Math.max(5, Math.min(100, opacity));
		hudLines = Math.max(0, Math.min(15, hudLines));
		if (parseColor(color) < 0) {
			color = "#00FFFF";
		}
	}

	/** Возвращает RGB (0xRRGGBB) или -1, если строка некорректна. */
	public static int parseColor(String text) {
		if (text == null) {
			return -1;
		}
		String s = text.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (s.length() != 6) {
			return -1;
		}
		try {
			return Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public float red() {
		return ((parseColor(color) >> 16) & 0xFF) / 255f;
	}

	public float green() {
		return ((parseColor(color) >> 8) & 0xFF) / 255f;
	}

	public float blue() {
		return (parseColor(color) & 0xFF) / 255f;
	}

	public float alpha() {
		return opacity / 100f;
	}

	/** Приводит ввод пользователя к полному id блока. Возвращает null, если такого блока нет. */
	public static String normalizeBlockId(String input) {
		if (input == null) {
			return null;
		}
		String s = input.trim().toLowerCase();
		if (s.isEmpty()) {
			return null;
		}
		if (!s.contains(":")) {
			s = "minecraft:" + s;
		}
		Identifier id = Identifier.tryParse(s);
		if (id == null) {
			return null;
		}
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty() || block.get() == Blocks.AIR) {
			return null;
		}
		return id.toString();
	}

	/** Множество блоков, которые нужно искать. */
	public Set<Block> targetBlocks() {
		Set<Block> set = new LinkedHashSet<>();
		for (String s : blocks) {
			String normalized = normalizeBlockId(s);
			if (normalized != null) {
				BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(normalized)).ifPresent(set::add);
			}
		}
		return set;
	}

	/** Карта блок -> цвет для рендеринга. */
	public java.util.Map<Block, Integer> targetColors() {
		java.util.Map<Block, Integer> map = new java.util.LinkedHashMap<>();
		int defaultColor = parseColor(color);
		for (String s : blocks) {
			String normalized = normalizeBlockId(s);
			if (normalized != null) {
				Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(normalized));
				if (block.isPresent()) {
					String custom = blockColors.get(normalized);
					int rgb = custom != null ? parseColor(custom) : -1;
					map.put(block.get(), rgb >= 0 ? rgb : defaultColor);
				}
			}
		}
		return map;
	}

	public static String formatColor(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}
}
