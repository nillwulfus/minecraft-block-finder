package com.blockfinder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Меню настроек подсветки. */
public class BlockFinderScreen extends Screen {
	private final Screen parent;
	private final BlockFinderConfig cfg = BlockFinderClient.config();

	private EditBox blocksBox;
	private EditBox colorBox;

	public BlockFinderScreen(Screen parent) {
		super(Component.literal("Block Finder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = 220;
		int left = this.width / 2 - w / 2;
		int y = 42;

		blocksBox = new EditBox(this.font, left, y, w, 20, Component.literal("Блоки"));
		blocksBox.setMaxLength(512);
		blocksBox.setValue(String.join(", ", cfg.blocks));
		addRenderableWidget(blocksBox);

		y += 34;
		addRenderableWidget(Button.builder(toggleText("Подсветка", cfg.enabled), b -> {
			cfg.enabled = !cfg.enabled;
			b.setMessage(toggleText("Подсветка", cfg.enabled));
		}).bounds(left, y, w, 20).build());

		y += 24;
		addRenderableWidget(Button.builder(toggleText("Сквозь блоки", cfg.throughWalls), b -> {
			cfg.throughWalls = !cfg.throughWalls;
			b.setMessage(toggleText("Сквозь блоки", cfg.throughWalls));
		}).bounds(left, y, w, 20).build());

		y += 24;
		addRenderableWidget(Button.builder(toggleText("Координаты на экране", cfg.showHud), b -> {
			cfg.showHud = !cfg.showHud;
			b.setMessage(toggleText("Координаты на экране", cfg.showHud));
		}).bounds(left, y, w, 20).build());

		y += 24;
		addRenderableWidget(new IntSlider(left, y, w, 20, "Радиус поиска", 4, BlockFinderConfig.MAX_RADIUS, cfg.radius,
				v -> cfg.radius = v));

		y += 24;
		addRenderableWidget(new IntSlider(left, y, w, 20, "Макс. блоков", 1, BlockFinderConfig.MAX_BLOCKS, cfg.maxBlocks,
				v -> cfg.maxBlocks = v));

		y += 24;
		addRenderableWidget(new IntSlider(left, y, w, 20, "Непрозрачность %", 5, 100, cfg.opacity,
				v -> cfg.opacity = v));

		y += 34;
		colorBox = new EditBox(this.font, left, y, w, 20, Component.literal("Цвет"));
		colorBox.setMaxLength(7);
		colorBox.setValue(cfg.color);
		addRenderableWidget(colorBox);

		y += 28;
		addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
				.bounds(left, y, w, 20).build());
	}

	private static Component toggleText(String name, boolean value) {
		return Component.literal(name + ": " + (value ? "вкл" : "выкл"));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int left = this.width / 2 - 110;
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
		graphics.drawString(this.font, "Блоки через запятую (например: diamond_ore, minecraft:chest)", left, 30, 0xFFA0A0A0);
		graphics.drawString(this.font, "Цвет подсветки (#RRGGBB)", left, colorBox.getY() - 11, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		applyText();
		cfg.save();
		BlockScanner.forceRescan();
		this.minecraft.setScreen(parent);
	}

	private void applyText() {
		List<String> ids = new ArrayList<>();
		for (String part : blocksBox.getValue().split(",")) {
			String id = BlockFinderConfig.normalizeBlockId(part);
			if (id != null && !ids.contains(id)) {
				ids.add(id);
			}
		}
		cfg.blocks = ids;
		if (BlockFinderConfig.parseColor(colorBox.getValue()) >= 0) {
			String c = colorBox.getValue().trim();
			cfg.color = c.startsWith("#") ? c : "#" + c;
		}
	}

	/** Ползунок с целыми значениями. */
	private static class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final java.util.function.IntConsumer onChange;

		IntSlider(int x, int y, int w, int h, String label, int min, int max, int value,
				java.util.function.IntConsumer onChange) {
			super(x, y, w, h, Component.empty(), (value - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.onChange = onChange;
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(this.value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + current()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}
}
