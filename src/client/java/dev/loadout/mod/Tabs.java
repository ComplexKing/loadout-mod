package dev.loadout.mod;

import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The row that moves between Loadout's screens.
 *
 * <p>Shared so the split between them stays obvious. Mods change on the next launch and
 * packs change now; those are different enough to deserve separate screens, and separate
 * screens are only usable if getting between them is one click.
 */
final class Tabs {
	private Tabs() {
	}

	/** @param parent the screen to return to, carried across so the chain does not grow */
	static LinearLayout row(Screen current, Screen parent) {
		LinearLayout tabs = LinearLayout.horizontal();
		tabs.defaultCellSetting().padding(2);

		add(tabs, current, "Mods", LoadoutScreen.class, () -> new LoadoutScreen(parent));
		add(tabs, current, "Resource packs", LoadoutPacksScreen.class, () -> new LoadoutPacksScreen(parent));
		return tabs;
	}

	private static void add(LinearLayout tabs, Screen current, String label,
			Class<? extends Screen> screen, java.util.function.Supplier<Screen> open) {
		Function<String, Component> text = Component::literal;

		Button button = Button.builder(text.apply(label),
				b -> Minecraft.getInstance().gui.setScreen(open.get())).width(110).build();

		// The tab you are already on is left in place but dead, so the row does not move
		// underneath the pointer as you switch between them.
		if (screen.isInstance(current)) {
			button.active = false;
		}

		tabs.addChild(button);
	}
}
