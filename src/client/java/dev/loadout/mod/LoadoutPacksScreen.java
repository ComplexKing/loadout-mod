package dev.loadout.mod;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Resource packs, switched on and off without leaving the game.
 *
 * <p>The live half of an instance. Unlike the mod screen, nothing here is recorded for
 * later: pressing a button rebuilds the game's resources around the change and the world
 * looks different a second afterwards. Keeping the two on separate screens is deliberate --
 * see {@link LivePacks} for why one list covering both would be worse.
 */
public final class LoadoutPacksScreen extends Screen {
	private static final Component TITLE = Component.literal("Resource packs");

	private final Screen parent;
	private LinearLayout root = LinearLayout.vertical();

	private List<LivePacks.Entry> packs = List.of();
	private String problem;

	public LoadoutPacksScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.packs = LivePacks.rescan();
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		this.root = LinearLayout.vertical();
		this.root.defaultCellSetting().alignHorizontallyCenter().padding(4);

		this.root.addChild(new StringWidget(TITLE, this.font));
		this.root.addChild(Tabs.row(this, this.parent));
		this.root.addChild(new StringWidget(
				Component.literal("These apply straight away.").withStyle(ChatFormatting.GRAY),
				this.font));

		if (this.problem != null) {
			this.root.addChild(new StringWidget(
					Component.literal(this.problem).withStyle(ChatFormatting.RED), this.font));
		}

		if (this.packs.isEmpty()) {
			this.root.addChild(new StringWidget(
					Component.literal("No resource packs in this instance.")
							.withStyle(ChatFormatting.GRAY),
					this.font));
		} else {
			this.root.addChild(buildList());
		}

		LinearLayout footer = LinearLayout.horizontal();
		footer.defaultCellSetting().padding(4);
		footer.addChild(Button.builder(Component.literal("Look for new"), button -> {
			// The folder is rescanned rather than trusted, because the launcher may have
			// downloaded something into it since this screen opened.
			this.problem = null;
			this.packs = LivePacks.rescan();
			rebuild();
		}).width(110).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE,
				button -> this.minecraft.gui.setScreen(this.parent)).width(110).build());
		this.root.addChild(footer);

		this.root.arrangeElements();
		this.root.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private ScrollableLayout buildList() {
		GridLayout grid = new GridLayout();
		grid.defaultCellSetting().padding(2);

		int row = 0;
		for (LivePacks.Entry pack : this.packs) {
			Component name = pack.title().copy().withStyle(style(pack));
			grid.addChild(new StringWidget(220, 20, name, this.font), row, 0);

			Button toggle = Button.builder(
					Component.literal(pack.selected() ? "On" : "Off")
							.withStyle(pack.selected() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
					button -> toggle(pack)).width(50).build();

			// A pack the game requires is shown rather than hidden -- it is part of what is
			// loaded -- but there is nothing to press.
			if (pack.locked()) {
				toggle.active = false;
				toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.literal("The game requires this pack.")));
			} else if (!pack.compatible()) {
				toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.literal("Made for a different version of Minecraft.")));
			}

			grid.addChild(toggle, row, 1);
			row++;
		}

		ScrollableLayout scroller = new ScrollableLayout(this.minecraft, grid,
				Math.max(this.height - 120, 60));
		scroller.setMinWidth(300);
		return scroller;
	}

	private ChatFormatting style(LivePacks.Entry pack) {
		if (!pack.compatible()) {
			return ChatFormatting.RED;      // will load, probably badly
		}
		if (pack.builtIn()) {
			return ChatFormatting.AQUA;     // not a file anyone put there
		}
		return pack.selected() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
	}

	/**
	 * Applies the change, then re-reads what actually happened.
	 *
	 * <p>Not optimistic, unlike the mod screen's toggles. There the request is the slow part
	 * and the answer is never in doubt; here the game itself decides -- a pack can fail to
	 * load and be rolled back -- so the list is rebuilt from the repository afterwards
	 * rather than from what was asked for.
	 */
	private void toggle(LivePacks.Entry pack) {
		this.problem = LivePacks.setSelected(pack.id(), !pack.selected());
		this.packs = LivePacks.current();
		rebuild();
	}

	@Override
	protected void repositionElements() {
		this.root.arrangeElements();
		FrameLayout.centerInRectangle(this.root, this.getRectangle());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
