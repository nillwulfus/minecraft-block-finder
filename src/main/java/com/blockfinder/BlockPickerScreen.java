package com.blockfinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Выбор искомых блоков кликом по иконке. */
public class BlockPickerScreen extends Screen {
	private static final int W = 380;
	private static final int H = 236;
	private static final int CELL = 22;
	private static final int COLS = 15;
	private static final int GRID_X = 16;
	private static final int GRID_Y = 62;
	private static final int GRID_W = COLS * CELL;
	private static final int GRID_H = 6 * CELL;

	private static final String[] CHIPS = { "Все", "Руды", "Выбранные" };

	private record Entry(ItemStack stack, String id, String search, Component name) {
	}

	private static List<Entry> cache;

	private final Screen parent;
	private final BlockFinderConfig cfg = BlockFinderClient.config();
	private final Set<String> selected = new LinkedHashSet<>(cfg.blocks);
	private final List<Entry> view = new ArrayList<>();

	private int px;
	private int py;
	private EditBox searchBox;
	private int filter = 0;
	private String query = "";
	private float scroll = 0;
	private boolean draggingBar = false;

	public BlockPickerScreen(Screen parent) {
		super(Component.literal("Выбор блоков"));
		this.parent = parent;
	}

	private static List<Entry> entries() {
		if (cache == null) {
			List<Entry> list = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				Item item = block.asItem();
				if (item == Items.AIR) {
					continue;
				}
				Identifier key = BuiltInRegistries.BLOCK.getKey(block);
				Component name = block.getName();
				String id = key.toString();
				list.add(new Entry(new ItemStack(item), id, (name.getString() + " " + id).toLowerCase(Locale.ROOT), name));
			}
			cache = list;
		}
		return cache;
	}

	private static boolean isOre(String id) {
		return id.endsWith("_ore") || id.endsWith("ancient_debris");
	}

	@Override
	protected void init() {
		px = (this.width - W) / 2;
		py = (this.height - H) / 2;

		searchBox = new EditBox(this.font, px + 16, py + 34, 160, 18, Component.literal("Поиск"));
		searchBox.setHint(Component.literal("Поиск блока..."));
		searchBox.setMaxLength(40);
		searchBox.setValue(query);
		searchBox.setResponder(s -> {
			query = s;
			scroll = 0;
			refreshView();
		});
		addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		refreshView();
	}

	private void refreshView() {
		view.clear();
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		for (Entry e : entries()) {
			if (filter == 1 && !isOre(e.id())) {
				continue;
			}
			if (filter == 2 && !selected.contains(e.id())) {
				continue;
			}
			if (!q.isEmpty() && !e.search().contains(q)) {
				continue;
			}
			view.add(e);
		}
		clampScroll();
	}

	private int maxScroll() {
		int rows = (view.size() + COLS - 1) / COLS;
		return Math.max(0, rows * CELL - GRID_H);
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
	}

	// ----- геометрия -----

	private int gx() {
		return px + GRID_X;
	}

	private int gy() {
		return py + GRID_Y;
	}

	private int chipX(int index) {
		int x = px + 184;
		for (int i = 0; i < index; i++) {
			x += this.font.width(CHIPS[i]) + 14 + 4;
		}
		return x;
	}

	private int chipW(int index) {
		return this.font.width(CHIPS[index]) + 14;
	}

	private int barX() {
		return gx() + GRID_W + 4;
	}

	private int thumbH() {
		return Math.max(16, GRID_H * GRID_H / (GRID_H + maxScroll()));
	}

	private int thumbY() {
		int max = maxScroll();
		if (max == 0) {
			return gy();
		}
		return gy() + Math.round(scroll / max * (GRID_H - thumbH()));
	}

	// ----- отрисовка -----

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xC0101018, 0xD0000000);
		Ui.panel(g, px, py, W, H);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		g.fill(px + 16, py + 10, px + 19, py + 21, Ui.ACCENT);
		Ui.text(g, this.font, "Выбор блоков", px + 25, py + 12, Ui.TEXT);
		Ui.textRight(g, this.font, "Клик по иконке — выбрать / убрать", px + W - 16, py + 12, Ui.MUTED);
		g.fill(px + 16, py + 27, px + W - 16, py + 28, Ui.BORDER);

		for (int i = 0; i < CHIPS.length; i++) {
			Ui.chip(g, this.font, CHIPS[i], chipX(i), py + 34, chipW(i), 18, filter == i,
					Ui.in(mouseX, mouseY, chipX(i), py + 34, chipW(i), 18));
		}

		renderGrid(g, mouseX, mouseY);
		renderScrollbar(g);

		Ui.text(g, this.font, "Выбрано: " + selected.size(), px + 16, py + 211, Ui.TEXT);
		Ui.button(g, this.font, "+ Все руды", px + 94, py + 204, 82, 22,
				Ui.in(mouseX, mouseY, px + 94, py + 204, 82, 22), false);
		Ui.button(g, this.font, "Сбросить", px + 180, py + 204, 90, 22,
				Ui.in(mouseX, mouseY, px + 180, py + 204, 90, 22), false);
		Ui.button(g, this.font, "Готово", px + W - 16 - 90, py + 204, 90, 22,
				Ui.in(mouseX, mouseY, px + W - 16 - 90, py + 204, 90, 22), true);
	}

	private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
		int gx = gx();
		int gy = gy();

		if (view.isEmpty()) {
			Ui.textCentered(g, this.font, "Ничего не найдено", gx + GRID_W / 2, gy + GRID_H / 2 - 4, Ui.MUTED);
			return;
		}

		boolean mouseInGrid = Ui.in(mouseX, mouseY, gx, gy, GRID_W, GRID_H);
		Entry hovered = null;

		g.enableScissor(gx, gy, gx + GRID_W, gy + GRID_H);
		int firstRow = (int) (scroll / CELL);
		int rows = GRID_H / CELL + 2;
		outer:
		for (int r = firstRow; r < firstRow + rows; r++) {
			for (int c = 0; c < COLS; c++) {
				int index = r * COLS + c;
				if (index >= view.size()) {
					break outer;
				}
				Entry e = view.get(index);
				int x = gx + c * CELL;
				int y = gy + r * CELL - (int) scroll;
				boolean hover = mouseInGrid && Ui.in(mouseX, mouseY, x, y, CELL, CELL);
				boolean sel = selected.contains(e.id());

				int bg = sel ? Ui.ACCENT_DARK : (hover ? Ui.CARD_HOVER : Ui.CARD);
				Ui.round(g, x + 1, y + 1, x + CELL - 1, y + CELL - 1, bg);
				if (sel) {
					g.renderOutline(x + 1, y + 1, CELL - 2, CELL - 2, Ui.ACCENT);
				} else if (hover) {
					g.renderOutline(x + 1, y + 1, CELL - 2, CELL - 2, Ui.MUTED);
				}
				g.renderItem(e.stack(), x + 3, y + 3);
				if (sel) {
					g.fill(x + 3, y + CELL - 4, x + CELL - 3, y + CELL - 2, 0xFF000000 | cfg.rgbFor(e.id()));
				}

				if (hover) {
					hovered = e;
				}
			}
		}
		g.disableScissor();

		if (hovered != null) {
			List<Component> tip = new ArrayList<>();
			tip.add(hovered.name());
			tip.add(Component.literal(hovered.id()).withStyle(ChatFormatting.GRAY));
			if (selected.contains(hovered.id())) {
				tip.add(Component.literal("Клик — убрать").withStyle(ChatFormatting.DARK_AQUA));
				tip.add(Component.literal("ПКМ — свой цвет блока").withStyle(ChatFormatting.DARK_AQUA));
			} else {
				tip.add(Component.literal("Клик — выбрать").withStyle(ChatFormatting.DARK_AQUA));
			}
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private void renderScrollbar(GuiGraphics g) {
		int x = barX();
		g.fill(x, gy(), x + 6, gy() + GRID_H, Ui.CARD);
		if (maxScroll() > 0) {
			Ui.round(g, x, thumbY(), x + 6, thumbY() + thumbH(), draggingBar ? Ui.ACCENT_HOVER : Ui.ACCENT);
		}
	}

	// ----- ввод -----

	private void dragBarTo(double my) {
		int track = GRID_H - thumbH();
		if (track <= 0) {
			return;
		}
		double t = (my - gy() - thumbH() / 2.0) / track;
		scroll = (float) (Math.max(0, Math.min(1, t)) * maxScroll());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1 && Ui.in(event.x(), event.y(), gx(), gy(), GRID_W, GRID_H)) {
			int col = (int) ((event.x() - gx()) / CELL);
			int row = (int) ((event.y() - gy() + scroll) / CELL);
			int index = row * COLS + col;
			if (col >= 0 && col < COLS && index >= 0 && index < view.size()) {
				String id = view.get(index).id();
				if (selected.contains(id)) {
					cfg.blocks = new ArrayList<>(selected);
					cfg.save();
					this.minecraft.setScreen(new BlockColorScreen(this, id));
				}
			}
			return true;
		}
		if (event.button() == 0) {
			double mx = event.x();
			double my = event.y();

			for (int i = 0; i < CHIPS.length; i++) {
				if (Ui.in(mx, my, chipX(i), py + 34, chipW(i), 18)) {
					filter = i;
					scroll = 0;
					refreshView();
					return true;
				}
			}

			if (Ui.in(mx, my, barX() - 2, gy(), 10, GRID_H) && maxScroll() > 0) {
				draggingBar = true;
				dragBarTo(my);
				return true;
			}

			if (Ui.in(mx, my, gx(), gy(), GRID_W, GRID_H)) {
				int col = (int) ((mx - gx()) / CELL);
				int row = (int) ((my - gy() + scroll) / CELL);
				int index = row * COLS + col;
				if (col >= 0 && col < COLS && index >= 0 && index < view.size()) {
					String id = view.get(index).id();
					if (!selected.remove(id)) {
						selected.add(id);
					}
					if (filter == 2) {
						refreshView();
					}
				}
				return true;
			}

			if (Ui.in(mx, my, px + 94, py + 204, 82, 22)) {
				for (Entry e : entries()) {
					if (isOre(e.id())) {
						selected.add(e.id());
					}
				}
				refreshView();
				return true;
			}
			if (Ui.in(mx, my, px + 180, py + 204, 90, 22)) {
				selected.clear();
				refreshView();
				return true;
			}
			if (Ui.in(mx, my, px + W - 16 - 90, py + 204, 90, 22)) {
				onClose();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingBar) {
			dragBarTo(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingBar) {
			draggingBar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (float) (scrollY * CELL);
		clampScroll();
		return true;
	}

	@Override
	public void onClose() {
		cfg.blocks = new ArrayList<>(selected);
		cfg.save();
		BlockScanner.forceRescan();
		this.minecraft.setScreen(parent);
	}
}
