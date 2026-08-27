package dev.loadout.mod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Opens Loadout's screens from Mod Menu's mod list.
 *
 * <p>A second way in, for the same reason the mod screen carries a button for the frame
 * time overlay: a keybind is only discoverable by somebody who already knows it exists.
 * Anyone browsing their mod list will find this without being told.
 *
 * <p>Mod Menu calls it a config screen and it is not one — there is nothing here to
 * configure. It is the mod list, which is what somebody clicking a launcher companion's
 * button is looking for.
 *
 * <p>Loaded reflectively by Mod Menu through its own entrypoint, so this class is only
 * touched when Mod Menu is installed. Nothing else in the mod refers to it, and the
 * dependency is compile-only — the mod runs perfectly well without it.
 */
public final class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LoadoutScreen::new;
	}
}
