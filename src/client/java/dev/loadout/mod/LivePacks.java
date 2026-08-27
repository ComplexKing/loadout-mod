package dev.loadout.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * The half of an instance that really can change while the game is running.
 *
 * <h2>Why packs are different from mods</h2>
 *
 * <p>A mod is decided during bootstrap and cannot be added or removed afterwards; the mod
 * screen is honest about only recording changes for the next launch. Resource packs are the
 * opposite -- the game already knows how to throw its resources away and build them again,
 * because that is what the vanilla pack screen does every time somebody presses Done. So
 * turning one on here turns it on, now.
 *
 * <p>Only resource packs, though. A shader pack is a loader's idea rather than the
 * game's, and the loader that reads shaderpacks/ does not run on 26.2's Vulkan path at
 * all, so there is nothing here to switch on. The launcher still installs them into the
 * folder, for whenever that changes.
 *
 * <p>The two are kept on separate screens for exactly that reason. One word covering both
 * would make each of them harder to trust: nobody should have to remember which half of a
 * list applies immediately.
 *
 * <h2>Where the truth lives</h2>
 *
 * <p>The list comes from Minecraft's own repository rather than from the launcher. The
 * launcher knows what it installed; the repository knows what is loaded, which includes
 * built-in packs, packs another mod supplies, and anything dropped into the folder by hand.
 * For "what is on right now", the game is the authority and asking anything else would be
 * inventing a second answer.
 */
public final class LivePacks {
	private LivePacks() {
	}

	/**
	 * @param builtIn a pack the game supplies rather than a file in the folder
	 * @param locked required by the game, so it cannot be switched off at all
	 */
	public record Entry(
			String id,
			Component title,
			Component description,
			boolean selected,
			boolean compatible,
			boolean builtIn,
			boolean locked
	) {
	}

	private static PackRepository repository() {
		return Minecraft.getInstance().getResourcePackRepository();
	}

	/**
	 * Every pack the game can see, selected ones first, after rescanning the folder.
	 *
	 * <p>The rescan is what lets a pack the launcher downloaded a moment ago show up here
	 * rather than needing a trip through the vanilla screen to be noticed.
	 */
	public static List<Entry> rescan() {
		repository().reload();
		return current();
	}

	/**
	 * The same list from what the repository already holds.
	 *
	 * <p>Used straight after a toggle. Applying one starts a reload that is already
	 * rescanning and opening these packs on another thread, and rescanning a third time
	 * underneath it would be asking for a race in exchange for nothing -- turning a pack on
	 * cannot have changed what is in the folder.
	 */
	public static List<Entry> current() {
		PackRepository repository = repository();
		Set<String> selected = Set.copyOf(repository.getSelectedIds());

		List<Entry> on = new ArrayList<>();
		List<Entry> off = new ArrayList<>();
		for (Pack pack : repository.getAvailablePacks()) {
			boolean isOn = selected.contains(pack.getId());
			Entry entry = new Entry(
					pack.getId(),
					pack.getTitle(),
					pack.getDescription(),
					isOn,
					pack.getCompatibility().isCompatible(),
					// "file/" is what the folder source prefixes its ids with; anything
					// else came from the game or from another mod.
					!pack.getId().startsWith("file/"),
					pack.isRequired());

			(isOn ? on : off).add(entry);
		}

		on.addAll(off);
		return List.copyOf(on);
	}

	/**
	 * Turns a pack on or off and rebuilds the game's resources around the change.
	 *
	 * <p>{@code updateResourcePacks} is vanilla's own commit: it rewrites the selection in
	 * options.txt, saves, and reloads only if something actually changed. Reusing it rather
	 * than reloading directly is what keeps a change made here indistinguishable from one
	 * made in the vanilla screen -- including surviving the next launch.
	 *
	 * @return an error to show, or null if it worked
	 */
	public static String setSelected(String id, boolean selected) {
		Minecraft client = Minecraft.getInstance();
		PackRepository repository = client.getResourcePackRepository();

		boolean changed = selected ? repository.addPack(id) : repository.removePack(id);
		if (!changed) {
			// removePack refuses a pack the game requires, and addPack refuses one that is
			// already on or has since disappeared from the folder.
			return selected
					? "That pack is no longer there."
					: "That pack cannot be turned off.";
		}

		client.options.updateResourcePacks(repository);
		return null;
	}
}
