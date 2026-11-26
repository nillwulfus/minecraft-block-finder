package com.blockfinder;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Универсальное окно выбора цвета: колесо, яркость, HEX, кнопки «Сброс» и «Готово». */
public class ColorDialogScreen extends Screen {
	private static final int W = 196;
	private static final int H = 236;

	private final Screen parent;
	private final String heading;
	private final int defaultRgb;
	private final IntConsumer onChange;
	private final int startRgb;

	private int px;
	private int py;
	private ColorPanel panel;
	private boolean dragging;

	public ColorDialogScreen(Screen parent, String heading, int startRgb, int defaultRgb, IntConsumer onChange) {
		super(Component.literal(heading));
		this.parent = parent;
		this.heading = heading;
		this.startRgb = startRgb;
		this.defaultRgb = defaultRgb;
		this.onChange = onChange;
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;
		panel = new ColorPanel(startRgb, onChange);
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

		g.fill(px + 16, py + 10, px + 19, py + 21, Ui.ACCENT);
		Ui.text(g, this.font, heading, px + 25, py + 12, Ui.TEXT);
		g.fill(px + 16, py + 27, px + W - 16, py + 28, Ui.BORDER);
		Ui.text(g, this.font, "Выберите цвет", px + 16, py + 33, Ui.MUTED);

		panel.render(g);

		int by = py + H - 30;
		Ui.button(g, this.font, "Сброс", px + 16, by, 80, 22, Ui.in(mouseX, mouseY, px + 16, by, 80, 22), false);
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
				panel.setRgb(defaultRgb);
				onChange.accept(defaultRgb);
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
		this.minecraft.setScreen(parent);
	}
}
