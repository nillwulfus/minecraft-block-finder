package com.blockfinder;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Переиспользуемый выбор цвета: цветовое колесо + полоса яркости + образец + поле HEX.
 * Размер 156 x 150, левый верхний угол задаётся через {@link #layout(int, int)}.
 */
final class ColorPanel {
	static final int WIDTH = 156;
	static final int HEIGHT = 150;
	private static final int R = 52;

	private static final int DRAG_NONE = 0;
	private static final int DRAG_WHEEL = 1;
	private static final int DRAG_BAR = 2;

	private final IntConsumer onChange;
	private float hue;
	private float sat;
	private float val;
	private int ox;
	private int oy;
	private int drag = DRAG_NONE;
	private boolean syncing;
	private EditBox hexBox;

	ColorPanel(int rgb, IntConsumer onChange) {
		this.onChange = onChange;
		setHsvFrom(rgb);
	}

	void layout(int x, int y) {
		this.ox = x;
		this.oy = y;
	}

	EditBox createHexBox(Font font) {
		hexBox = new EditBox(font, ox + 30, oy + 130, WIDTH - 30, 20, Component.literal("Цвет"));
		hexBox.setMaxLength(7);
		hexBox.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
		hexBox.setValue(hex(rgb()));
		hexBox.setResponder(text -> {
			if (syncing) {
				return;
			}
			int c = BlockFinderConfig.parseColor(text);
			if (c >= 0) {
				setHsvFrom(c);
				onChange.accept(c);
			}
		});
		return hexBox;
	}

	void setVisible(boolean visible) {
		if (hexBox != null) {
			hexBox.visible = visible;
			hexBox.active = visible;
		}
	}

	int rgb() {
		return Ui.hsv(hue, sat, val);
	}

	/** Установить цвет снаружи (например, сброс к общему). */
	void setRgb(int rgb) {
		setHsvFrom(rgb);
		syncHex();
	}

	private static String hex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	private void setHsvFrom(int rgb) {
		float[] c = Ui.rgbToHsv(Math.max(0, rgb));
		hue = c[0];
		sat = c[1];
		val = c[2];
	}

	private void syncHex() {
		if (hexBox != null) {
			syncing = true;
			hexBox.setValue(hex(rgb()));
			syncing = false;
		}
	}

	private void changed() {
		syncHex();
		onChange.accept(rgb());
	}

	// ----- отрисовка -----

	void render(GuiGraphics g) {
		int cx = ox + WIDTH / 2;
		int cy = oy + 54;

		for (int dy = -R - 2; dy < R + 2; dy += 2) {
			for (int dx = -R - 2; dx < R + 2; dx += 2) {
				double d = Math.hypot(dx + 1, dy + 1);
				if (d <= R + 2.5 && d > R - 0.5) {
					g.fill(cx + dx, cy + dy, cx + dx + 2, cy + dy + 2, Ui.BORDER);
				}
			}
		}
		for (int dy = -R; dy < R; dy += 2) {
			for (int dx = -R; dx < R; dx += 2) {
				double px = dx + 1;
				double py = dy + 1;
				double d = Math.hypot(px, py);
				if (d > R) {
					continue;
				}
				float h = (float) ((Math.atan2(py, px) / (2 * Math.PI) + 1.0) % 1.0);
				g.fill(cx + dx, cy + dy, cx + dx + 2, cy + dy + 2, 0xFF000000 | Ui.hsv(h, (float) (d / R), val));
			}
		}

		double ang = hue * 2 * Math.PI;
		int mx = cx + (int) Math.round(Math.cos(ang) * sat * R);
		int my = cy + (int) Math.round(Math.sin(ang) * sat * R);
		g.fill(mx - 4, my - 4, mx + 4, my + 4, 0xFF000000);
		g.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFFFFFFFF);
		g.fill(mx - 2, my - 2, mx + 2, my + 2, 0xFF000000 | rgb());

		int by = oy + 112;
		Ui.round(g, ox - 1, by - 1, ox + WIDTH + 1, by + 11, Ui.BORDER);
		for (int i = 0; i < WIDTH; i += 2) {
			g.fill(ox + i, by, ox + i + 2, by + 10, 0xFF000000 | Ui.hsv(hue, sat, i / (float) (WIDTH - 1)));
		}
		int kx = ox + Math.round(val * (WIDTH - 1));
		g.fill(kx - 2, by - 2, kx + 3, by + 12, 0xFF000000);
		g.fill(kx - 1, by - 1, kx + 2, by + 11, 0xFFFFFFFF);

		Ui.round(g, ox, oy + 130, ox + 24, oy + 150, Ui.BORDER);
		g.fill(ox + 1, oy + 131, ox + 23, oy + 149, 0xFF000000 | rgb());
	}

	// ----- ввод (координаты экрана) -----

	boolean mouseClicked(double mx, double my) {
		double dx = mx - (ox + WIDTH / 2.0);
		double dy = my - (oy + 54);
		if (dx * dx + dy * dy <= (R + 3) * (R + 3)) {
			drag = DRAG_WHEEL;
			fromWheel(mx, my);
			return true;
		}
		if (Ui.in(mx, my, ox, oy + 110, WIDTH, 14)) {
			drag = DRAG_BAR;
			fromBar(mx);
			return true;
		}
		return false;
	}

	boolean mouseDragged(double mx, double my) {
		if (drag == DRAG_WHEEL) {
			fromWheel(mx, my);
			return true;
		}
		if (drag == DRAG_BAR) {
			fromBar(mx);
			return true;
		}
		return false;
	}

	/** @return true, если до этого шло перетаскивание (значит, цвет изменился - можно сохранять). */
	boolean mouseReleased() {
		boolean was = drag != DRAG_NONE;
		drag = DRAG_NONE;
		return was;
	}

	private void fromWheel(double mx, double my) {
		double dx = mx - (ox + WIDTH / 2.0);
		double dy = my - (oy + 54);
		double dist = Math.min(Math.sqrt(dx * dx + dy * dy), R);
		hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
		sat = (float) (dist / R);
		changed();
	}

	private void fromBar(double mx) {
		val = (float) Math.max(0, Math.min(1, (mx - ox) / (WIDTH - 1.0)));
		changed();
	}
}
