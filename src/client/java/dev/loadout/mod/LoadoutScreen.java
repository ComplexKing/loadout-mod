package dev.loadout.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
 * Managing this instance's mods without leaving the game.
 *
 * <h2>What this can and cannot change</h2>
 *
 * <p>Fabric decides what is loaded during bootstrap: it resolves the mod list, loads the
 * classes and applies every mixin before this screen could possibly exist. Nothing here
 * can add a mod to the running game or take one out of it, and no amount of effort would
 * change that -- it is the platform, not a gap.
 *
 * <p>So this screen is honest about which half of the job it is doing. Turning something
 * on or off is recorded against the next launch and labelled that way, and the footer says
 * how many changes are waiting rather than pretending they have happened. Resource packs
 * and shaders are the genuinely live half and belong on their own screen; lumping them in
 * here under one word would make both halves confusing.
 */
public final class LoadoutScreen extends Screen {
	private static final Component TITLE = Component.literal("Loadout");

	private final Screen parent;
	/**
	 * Rebuilt rather than emptied.
	 *
	 * <p>A layout has no way to drop its children, so reuse would mean appending to what is
	 * already there. Building a fresh one is both the supported way and the simpler one.
	 */
	private LinearLayout root = LinearLayout.vertical();

	/**
	 * What the launcher last told us, and what we have asked it to change.
	 *
	 * <p>Held separately so the list can be drawn immediately from what is known while a
	 * request is still in flight -- a screen that blanks itself on every click feels
	 * broken even when it is working.
	 */
	private List<LauncherApi.InstalledMod> mods = List.of();
	private final Map<String, Boolean> pending = new HashMap<>();

	private String problem;
	private boolean loading = true;

	public LoadoutScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		refresh();
		rebuild();
	}

	/**
	 * Asks the launcher for the mod list.
	 *
	 * <p>On a worker thread, and the result is applied back on the client thread. A screen
	 * runs on the render thread; a loopback request is fast but not instant, and blocking
	 * here would drop frames in a game that is still running behind this screen.
	 */
	private void refresh() {
		var launcher = LoadoutMod.launcher().orElse(null);
		if (launcher == null) {
			this.loading = false;
			this.problem = "This game was not started by Loadout.";
			return;
		}

		Thread.ofVirtual().name("loadout-fetch").start(() -> {
			List<LauncherApi.InstalledMod> found = List.of();
			String failure = null;

			try {
				found = launcher.mods();
			} catch (Exception e) {
				failure = e.getMessage() == null
						? "The launcher did not answer. Is it still open?"
						: e.getMessage();
			}

			final List<LauncherApi.InstalledMod> result = found;
			final String reported = failure;

			Minecraft.getInstance().execute(() -> {
				this.mods = result;
				this.problem = reported;
				this.loading = false;
				rebuild();
			});
		});
	}

	/** Rebuilds every widget. Cheap at this size, and simpler than patching them in place. */
	private void rebuild() {
		clearWidgets();
		this.root = LinearLayout.vertical();

		this.root.defaultCellSetting().alignHorizontallyCenter().padding(4);
		this.root.addChild(new StringWidget(
				Component.literal("Loadout")
						.append(Component.literal("  ").append(instanceLabel())
								.withStyle(ChatFormatting.GRAY)),
				this.font));

		if (this.loading) {
			this.root.addChild(new StringWidget(Component.literal("Reading the mod list..."), this.font));
		} else if (this.problem != null) {
			this.root.addChild(new StringWidget(
					Component.literal(this.problem).withStyle(ChatFormatting.RED), this.font));
		} else if (this.mods.isEmpty()) {
			this.root.addChild(new StringWidget(
					Component.literal("No mods installed in this instance.").withStyle(ChatFormatting.GRAY),
					this.font));
		} else {
			this.root.addChild(buildList());
		}

		if (!this.pending.isEmpty()) {
			this.root.addChild(new StringWidget(
					Component.literal(this.pending.size() + " change"
							+ (this.pending.size() == 1 ? "" : "s") + " will apply next launch")
							.withStyle(ChatFormatting.YELLOW),
					this.font));
		}

		LinearLayout footer = LinearLayout.horizontal();
		footer.defaultCellSetting().padding(4);
		footer.addChild(Button.builder(Component.literal("Refresh"), button -> {
			this.loading = true;
			rebuild();
			refresh();
		}).width(100).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE,
				button -> this.minecraft.gui.setScreen(this.parent)).width(100).build());
		this.root.addChild(footer);

		this.root.arrangeElements();
		this.root.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	private Component instanceLabel() {
		return LoadoutMod.launcher()
				.map(api -> Component.literal(api.instanceName()))
				.orElse(Component.empty());
	}

	private ScrollableLayout buildList() {
		GridLayout grid = new GridLayout();
		grid.defaultCellSetting().padding(2);

		int row = 0;
		for (LauncherApi.InstalledMod mod : this.mods) {
			boolean enabled = this.pending.getOrDefault(mod.fileName(), mod.enabled());

			grid.addChild(new StringWidget(220, 20,
					Component.literal(mod.modId() == null ? mod.fileName() : mod.modId())
							.withStyle(enabled ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
					this.font), row, 0);

			grid.addChild(Button.builder(
					Component.literal(enabled ? "On" : "Off")
							.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
					button -> toggle(mod)).width(50).build(), row, 1);

			row++;
		}

		// Capped so the list scrolls instead of pushing the buttons off the bottom on an
		// instance with fifty mods, which is a perfectly ordinary number of mods.
		ScrollableLayout scroller = new ScrollableLayout(this.minecraft, grid,
				Math.max(this.height - 120, 60));
		scroller.setMinWidth(300);
		return scroller;
	}

	/**
	 * Records a change and tells the launcher.
	 *
	 * <p>The button flips immediately and the request follows. A toggle that waits for a
	 * round trip feels broken even when the round trip is a millisecond, and the failure
	 * path puts it back -- so the only cost of being optimistic is a rare flick.
	 */
	private void toggle(LauncherApi.InstalledMod mod) {
		var launcher = LoadoutMod.launcher().orElse(null);
		if (launcher == null) {
			return;
		}

		boolean current = this.pending.getOrDefault(mod.fileName(), mod.enabled());
		boolean wanted = !current;

		if (wanted == mod.enabled()) {
			this.pending.remove(mod.fileName());   // back to where it started
		} else {
			this.pending.put(mod.fileName(), wanted);
		}
		rebuild();

		Thread.ofVirtual().name("loadout-toggle").start(() -> {
			try {
				launcher.setEnabledNextLaunch(mod.fileName(), wanted);
			} catch (Exception e) {
				Minecraft.getInstance().execute(() -> {
					this.pending.remove(mod.fileName());
					this.problem = "Could not change that: " + e.getMessage();
					rebuild();
				});
			}
		});
	}

	@Override
	protected void repositionElements() {
		this.root.arrangeElements();
		FrameLayout.centerInRectangle(this.root, this.getRectangle());
	}

	@Override
	public boolean isPauseScreen() {
		// Multiplayer does not pause anyway, and pausing singleplayer to change something
		// that only applies next launch would be a needless interruption.
		return false;
	}
}
