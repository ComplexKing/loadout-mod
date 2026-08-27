package dev.loadout.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Loadout companion mod.
 *
 * <p>Client only, and small on purpose. It reports what the launcher told it, marks
 * players known to be using the launcher, and does nothing else -- a mod that ships with
 * a launcher has to be the kind of thing somebody is happy to have running, which means
 * it should be obvious what it does and short enough to read.
 */
public final class LoadoutMod implements ClientModInitializer {
	public static final String MOD_ID = "loadout";
	private static final Logger LOG = LoggerFactory.getLogger("Loadout");

	/** The launcher that started this game, when there is one. */
	private static LauncherApi launcher;

	public static java.util.Optional<LauncherApi> launcher() {
		return java.util.Optional.ofNullable(launcher);
	}

	@Override
	public void onInitializeClient() {
		LOG.info("Loadout companion starting: {}", LaunchInfo.describe());

		// Looked up once. Whether the launcher is reachable is checked at the moment it is
		// used rather than now -- it can be closed while the game runs, and a value cached
		// at startup would be a stale promise.
		launcher = LauncherApi.available().orElse(null);
		if (launcher != null) {
			LOG.info("Connected to the launcher, managing instance '{}'", launcher.instanceName());
		}

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> reportStarted());

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// Granted on join rather than at startup, because the local player's UUID is
			// only settled once there is a session.
			if (LaunchInfo.launchedByLoadout()) {
				var player = Minecraft.getInstance().player;
				if (player != null) {
					BadgeRegistry.grant(player.getUUID(), BadgeRegistry.LOADOUT);
				}
			}
		});

		// Badges are knowledge about one session -- who was on that server, at that time.
		// Carrying them to the next server would mark players who are not there.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BadgeRegistry.clear());

		registerKeybind();

		// Registered always, drawn only when switched on. Registering it lazily would mean
		// deciding where in the HUD order it goes at an arbitrary moment.
		dev.loadout.mod.perf.PerfOverlay.register();
	}

	/**
	 * Tells the launcher this game got all the way up.
	 *
	 * <p>Fired once, from CLIENT_STARTED, which is as late as "starting" goes: mods are
	 * resolved, mixins are applied and the window exists. A game that fails before this
	 * point never reports, and that silence is exactly the signal -- the launcher can then
	 * offer the last mod set that did get here.
	 *
	 * <p>On a worker thread and failing quietly. This is bookkeeping; it must not delay the
	 * first frame, and a launcher that has already been closed is an ordinary situation
	 * rather than something to tell somebody about.
	 */
	private static void reportStarted() {
		LauncherApi api = launcher;
		if (api == null) {
			return;
		}

		Thread.ofVirtual().name("loadout-started").start(() -> {
			try {
				api.reportStarted();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				LOG.debug("Could not tell the launcher we started", e);
			}
		});
	}

	/**
	 * The key that opens the mod list.
	 *
	 * <p>Unbound would be safer against clashes, but a feature nobody can find is a feature
	 * nobody uses. Backslash is close to nothing else and is easy to change.
	 */
	private void registerKeybind() {
		KeyMapping.Category category =
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "loadout"));

		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.loadout.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_BACKSLASH,
				category));

		// F6 because vanilla binds nothing to it. If somebody else has, Minecraft's own
		// controls screen shows the clash and lets them settle it, which is more than a
		// mod deciding for them.
		KeyMapping perf = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.loadout.perf",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// A while loop rather than an if: consumeClick drains a queue, and presses that
			// happened during a lagging tick would otherwise be silently dropped.
			while (open.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new LoadoutScreen(null));
				}
			}

			while (perf.consumeClick()) {
				dev.loadout.mod.perf.PerfOverlay.setVisible(
						!dev.loadout.mod.perf.PerfOverlay.visible());
			}
		});
	}
}
