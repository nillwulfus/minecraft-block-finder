package com.blockfinder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Общий стиль и мелкие помощники отрисовки для экранов мода. */
final class Ui {
	static final int PANEL = 0xFF151922;
	static final int BORDER = 0xFF2B3345;
	static final int CARD = 0xFF1D2330;
	static final int CARD_HOVER = 0xFF283044;
	static final int TRACK = 0xFF3A4254;
	static final int ACCENT = 0xFF3FA9F5;
	static final int ACCENT_HOVER = 0xFF6BBFF8;
	static final int ACCENT_DARK = 0xFF1F3A52;
	static final int TEXT = 0xFFE8ECF4;
	static final int MUTED = 0xFF8B94A8;
	static final int DARK_TEXT = 0xFF0B1220;

	private Ui() {
	}

	static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Прямоугольник со срезанными углами (имитация скругления). */
	static void round(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1 + 2, y1, x2 - 2, y2, color);
		g.fill(x1, y1 + 2, x2, y2 - 2, color);
		g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
	}

	static void panel(GuiGraphics g, int x, int y, int w, int h) {
		round(g, x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
		round(g, x, y, x + w, y + h, PANEL);
	}

	static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
		g.drawString(font, s, x, y, color, true);
	}

	static void textRight(GuiGraphics g, Font font, String s, int right, int y, int color) {
		g.drawString(font, s, right - font.width(s), y, color, true);
	}

	static void textCentered(GuiGraphics g, Font font, String s, int cx, int y, int color) {
		g.drawString(font, s, cx - font.width(s) / 2, y, color, true);
	}

	static void button(GuiGraphics g, Font font, String label, int x, int y, int w, int h, boolean hover, boolean primary) {
		int bg = primary ? (hover ? ACCENT_HOVER : ACCENT) : (hover ? CARD_HOVER : CARD);
		round(g, x, y, x + w, y + h, bg);
		if (!primary) {
			g.renderOutline(x, y, w, h, hover ? ACCENT : BORDER);
		}
		textCentered(g, font, label, x + w / 2, y + (h - 8) / 2, primary ? DARK_TEXT : TEXT);
	}

	static void chip(GuiGraphics g, Font font, String label, int x, int y, int w, int h, boolean active, boolean hover) {
		int bg = active ? ACCENT_DARK : (hover ? CARD_HOVER : CARD);
		round(g, x, y, x + w, y + h, bg);
		g.renderOutline(x, y, w, h, active ? ACCENT : BORDER);
		textCentered(g, font, label, x + w / 2, y + (h - 8) / 2, active ? TEXT : MUTED);
	}

	/** Переключатель: тумблер справа в карточке. */
	static void toggleRow(GuiGraphics g, Font font, String label, boolean value, int x, int y, int w, int h, boolean hover) {
		round(g, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
		text(g, font, label, x + 8, y + (h - 8) / 2, TEXT);

		int tw = 28;
		int th = 12;
		int tx = x + w - tw - 8;
		int ty = y + (h - th) / 2;
		round(g, tx, ty, tx + tw, ty + th, value ? ACCENT : TRACK);
		int kx = value ? tx + tw - th + 2 : tx + 2;
		g.fill(kx, ty + 2, kx + th - 4, ty + th - 2, 0xFFFFFFFF);
	}

	/** Строка-переключатель по кругу: подпись слева, текущее значение справа. */
	static void cycleRow(GuiGraphics g, Font font, String label, String value, int x, int y, int w, int h, boolean hover) {
		round(g, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
		text(g, font, label, x + 8, y + (h - 8) / 2, TEXT);
		textRight(g, font, value, x + w - 8, y + (h - 8) / 2, ACCENT);
	}

	/** Строка с образцом цвета справа. */
	static void colorRow(GuiGraphics g, Font font, String label, int rgb, int x, int y, int w, int h, boolean hover) {
		round(g, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
		text(g, font, label, x + 8, y + (h - 8) / 2, TEXT);
		int sw = 26;
		int sx = x + w - sw - 8;
		int sy = y + 4;
		round(g, sx - 1, sy - 1, sx + sw + 1, sy + h - 7, hover ? ACCENT : BORDER);
		g.fill(sx, sy, sx + sw, sy + h - 8, 0xFF000000 | rgb);
	}

	/** Ползунок в карточке: подпись и значение сверху, дорожка снизу. */
	static void sliderRow(GuiGraphics g, Font font, String label, String shown, int value, int min, int max, int x, int y, int w, int h, boolean hover) {
		round(g, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
		text(g, font, label, x + 8, y + 3, MUTED);
		textRight(g, font, shown, x + w - 8, y + 3, TEXT);

		int trackX = x + 8;
		int trackW = w - 16;
		int trackY = y + h - 7;
		g.fill(trackX, trackY, trackX + trackW, trackY + 3, TRACK);
		float t = (value - min) / (float) (max - min);
		int fillW = Math.round(trackW * t);
		g.fill(trackX, trackY, trackX + fillW, trackY + 3, ACCENT);
		int kx = trackX + fillW;
		g.fill(kx - 2, trackY - 2, kx + 2, trackY + 5, 0xFFFFFFFF);
	}

	/** HSV (все компоненты 0..1) -> 0xRRGGBB. */
	static int hsv(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int i = (int) hh;
		float f = hh - i;
		float p = v * (1f - s);
		float q = v * (1f - s * f);
		float t = v * (1f - s * (1f - f));
		float r;
		float gr;
		float b;
		switch (i % 6) {
			case 0 -> { r = v; gr = t; b = p; }
			case 1 -> { r = q; gr = v; b = p; }
			case 2 -> { r = p; gr = v; b = t; }
			case 3 -> { r = p; gr = q; b = v; }
			case 4 -> { r = t; gr = p; b = v; }
			default -> { r = v; gr = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(gr * 255) << 8) | Math.round(b * 255);
	}

	/** 0xRRGGBB -> {h, s, v}. */
	static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0f) {
			h = 0f;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		if (h < 0f) {
			h += 1f;
		}
		float s = max == 0f ? 0f : d / max;
		return new float[] { h, s, max };
	}
}
