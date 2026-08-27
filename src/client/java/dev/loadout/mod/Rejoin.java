package dev.loadout.mod;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where the player is, so the next launch can put them back.
 *
 * <p>This is what makes a mod change cheap enough to actually make. A change only takes
 * effect on the next launch -- Fabric decided the mod list before the window existed --
 * so the question is not whether a restart happens but how much it costs. Landing back on
 * the same server turns two minutes of menus into about ten seconds, and that difference
 * is the whole feature.
 */
public final class Rejoin {
	/** Long names are servers; the button should not grow to fit one. */
	private static final int LABEL_LIMIT = 20;

	private Rejoin() {
	}

	/**
	 * @param type the value Minecraft's own quick-play arguments expect
	 * @param target a server address, or a world folder name
	 * @param label what to show on the button
	 */
	public record Target(String type, String target, String label) {
	}

	/**
	 * Where to return to, if anywhere.
	 *
	 * <p>Empty at the main menu, which is the honest answer: there is nothing to return to,
	 * and the button should say Restart rather than promise a rejoin.
	 *
	 * <p>Empty on Realms too. Quick play can reopen one, but by realm id rather than by
	 * anything the client hands out here, and a rejoin that opens the wrong world is worse
	 * than one that admits it cannot.
	 */
	public static Optional<Target> current() {
		Minecraft client = Minecraft.getInstance();

		// Checked first: a world opened to LAN is still this machine's own world, and the
		// thing to reopen is the save, not the address it is being served on.
		MinecraftServer local = client.getSingleplayerServer();
		if (local != null) {
			return levelId(local).map(id -> new Target("singleplayer", id, shorten(id)));
		}

		ServerData server = client.getCurrentServer();
		if (server == null || server.isRealm() || server.ip == null || server.ip.isBlank()) {
			return Optional.empty();
		}

		// The address is what reconnects; the name is only what the player calls it, and is
		// blank for a server joined by typing an address rather than saving it.
		String name = server.name == null || server.name.isBlank() ? server.ip : server.name;
		return Optional.of(new Target("multiplayer", server.ip, shorten(name)));
	}

	/**
	 * The save folder's name, which is what quick play reopens a world by.
	 *
	 * <p>Not the world's display name -- those two stop matching the moment somebody renames
	 * a world, and the folder is the one that stays true. {@code LevelResource.ROOT} is a
	 * literal ".", so the path needs normalising before its last element means anything.
	 */
	private static Optional<String> levelId(MinecraftServer server) {
		try {
			var name = server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
			return name == null ? Optional.empty() : Optional.of(name.toString());
		} catch (RuntimeException e) {
			// The storage handle is closed during shutdown. Nothing to return to by then.
			return Optional.empty();
		}
	}

	private static String shorten(String name) {
		return name.length() <= LABEL_LIMIT ? name : name.substring(0, LABEL_LIMIT - 3) + "...";
	}

	/**
	 * Asks the launcher to start a replacement, then closes this one down cleanly.
	 *
	 * <p>Ordered deliberately. The launcher is told first, so its work -- checking the
	 * install, staging the new mod set, starting a JVM -- overlaps with this game saving and
	 * quitting instead of following it. And if the launcher refuses, nothing has been closed
	 * and the refusal can be shown.
	 *
	 * <p>Then a real disconnect rather than a bare {@code stop()}. Quitting outright skips
	 * both things that matter here: a singleplayer world would not be saved, and a server
	 * would be left holding a session that has not said goodbye, which is how a rejoin
	 * arrives to be told it is already logged in.
	 *
	 * @param onFailure called on the client thread with a message to show; not called on
	 *     success, because on success there is shortly no screen to show anything on
	 */
	public static void restart(Target target, Consumer<String> onFailure) {
		LauncherApi launcher = LoadoutMod.launcher().orElse(null);
		if (launcher == null) {
			onFailure.accept("This game was not started by Loadout.");
			return;
		}

		// Off the render thread. The call itself is a loopback request that returns a job id
		// rather than waiting for the game to start, but a launcher that has been closed
		// costs the full connect timeout, and spending that in a button handler would look
		// exactly like a freeze.
		Thread.ofVirtual().name("loadout-relaunch").start(() -> {
			String failure = null;
			try {
				launcher.relaunch(target == null ? null : target.type(),
						target == null ? null : target.target());
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				failure = e.getMessage() == null
						? "The launcher did not answer. Is it still open?"
						: e.getMessage();
			}

			final String reported = failure;
			Minecraft.getInstance().execute(() -> {
				if (reported != null) {
					onFailure.accept(reported);
					return;
				}
				shutDown();
			});
		});
	}

	/** Save, say goodbye, quit. Run on the client thread. */
	private static void shutDown() {
		Minecraft client = Minecraft.getInstance();

		// Blocks until the integrated server has finished saving, drawing its own screen
		// while it waits, which is why it has to be the client thread that calls it.
		if (client.level != null) {
			client.disconnectFromWorld(Component.literal("Restarting to apply changes"));
		}

		client.stop();
	}
}
