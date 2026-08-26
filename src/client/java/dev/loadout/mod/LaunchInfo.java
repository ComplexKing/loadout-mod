package dev.loadout.mod;

import java.util.Optional;

/**
 * What the launcher told this game about itself.
 *
 * <p>Loadout passes a small number of system properties when it builds the command line.
 * A property rather than a file next to the instance: the game is started by a command
 * this launcher wrote, so the launcher already has a way to say things, and a file would
 * be one more thing to keep in step with a folder somebody may have copied.
 *
 * <p>Everything here is optional. The mod has to work when it was not launched by
 * Loadout at all -- somebody will drop it into another launcher's instance folder, and
 * the honest behaviour then is to do nothing rather than to guess.
 */
public final class LaunchInfo {
	/** Set by Loadout's own launch builder. Absent under any other launcher. */
	private static final String LAUNCHER_PROPERTY = "loadout.launcher";
	private static final String VERSION_PROPERTY = "loadout.version";
	private static final String INSTANCE_PROPERTY = "loadout.instance";

	private LaunchInfo() {
	}

	/**
	 * Whether Loadout started this game.
	 *
	 * <p>Deliberately not a claim about anything else. It is checked before showing a
	 * badge for the local player, and it is checked honestly: a mod that awarded itself a
	 * badge under every launcher would be advertising, not reporting.
	 */
	public static boolean launchedByLoadout() {
		return "loadout".equals(System.getProperty(LAUNCHER_PROPERTY));
	}

	public static Optional<String> launcherVersion() {
		return Optional.ofNullable(System.getProperty(VERSION_PROPERTY));
	}

	/** Which instance this is, as the launcher named it. Useful in logs and reports. */
	public static Optional<String> instanceName() {
		return Optional.ofNullable(System.getProperty(INSTANCE_PROPERTY));
	}

	/** A one-line summary for the log, so a bug report says which launcher was involved. */
	public static String describe() {
		if (!launchedByLoadout()) {
			return "not launched by Loadout";
		}
		return "Loadout " + launcherVersion().orElse("(unknown version)")
				+ instanceName().map(name -> ", instance '" + name + "'").orElse("");
	}
}
