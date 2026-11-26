package com.blockfinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Окно выбора собственного цвета подсветки для одного блока. */
public class BlockColorScreen extends Screen {
	private static final int W = 196;
	private static final int H = 236;

	private final Screen parent;
	private final String blockId;
	private final BlockFinderConfig cfg = BlockFinderClient.config();

	private int px;
	private int py;
	private ColorPanel panel;
	private boolean dragging;
	private ItemStack icon = ItemStack.EMPTY;
	private String blockName = "";

	public BlockColorScreen(Screen parent, String blockId) {
		super(Component.literal("Цвет блока"));
		this.parent = parent;
		this.blockId = blockId;
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;

		Identifier id = Identifier.tryParse(blockId);
		if (id != null) {
			Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block != null) {
				icon = new ItemStack(block.asItem());
				blockName = block.getName().getString();
			}
		}
		if (blockName.isEmpty()) {
			blockName = blockId;
		}

		panel = new ColorPanel(cfg.rgbFor(blockId), rgb -> cfg.blockColors.put(blockId, BlockFinderConfig.formatColor(rgb)));
		panel.layout(px + (W - ColorPanel.WIDTH) / 2, py + 46);
		addRenderableWidget(panel.createHexBox(this.font));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xC0101018, 0xD0000000);
		Ui.panel(g, px, py, W, H);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		g.renderItem(icon, px + 14, py + 5);
		Ui.text(g, this.font, blockName, px + 36, py + 10, Ui.TEXT);
		g.fill(px + 16, py + 27, px + W - 16, py + 28, Ui.BORDER);
		boolean custom = cfg.blockColors.containsKey(blockId);
		Ui.text(g, this.font, custom ? "Свой цвет этого блока" : "Сейчас: общий цвет", px + 16, py + 33, Ui.MUTED);

		panel.render(g);

		int by = py + H - 30;
		Ui.button(g, this.font, "Общий цвет", px + 16, by, 80, 22, Ui.in(mouseX, mouseY, px + 16, by, 80, 22), false);
		Ui.button(g, this.font, "Готово", px + W - 16 - 80, by, 80, 22,
				Ui.in(mouseX, mouseY, px + W - 16 - 80, by, 80, 22), true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();
			int by = py + H - 30;

			if (panel.mouseClicked(mx, my)) {
				dragging = true;
				return true;
			}
			if (Ui.in(mx, my, px + 16, by, 80, 22)) {
				cfg.blockColors.remove(blockId);
				panel.setRgb(cfg.defaultRgb());
				return true;
			}
			if (Ui.in(mx, my, px + W - 16 - 80, by, 80, 22)) {
				onClose();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			panel.mouseDragged(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			panel.mouseReleased();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		cfg.save();
		BlockScanner.forceRescan();
		this.minecraft.setScreen(parent);
	}
}
