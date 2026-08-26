package dev.loadout.mod;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has a badge, and where that knowledge comes from.
 *
 * <p>The interesting question for a badge like this is not how to draw it but how a
 * client learns that somebody else is also using the launcher. Minecraft gives clients no
 * way to talk to each other: a custom payload goes to the server, and only a server that
 * has been taught to relay it will pass anything on. So there are exactly three honest
 * answers, and this class is written so the drawing does not care which is in use:
 *
 * <ul>
 *   <li><b>Yourself</b>, which needs nothing -- the launcher already told this process.
 *   <li><b>A server that relays it</b>, which works on servers running the companion
 *       plugin and nowhere else.
 *   <li><b>A presence service</b> that clients report to and query, which works
 *       everywhere and means running a service that knows who is playing where.
 * </ul>
 *
 * <p>Only the first two are implemented. The third is a product decision rather than a
 * missing function, and building the seam for it without building it is the point of this
 * class: nothing else in the mod has to change if that decision is made later.
 */
public final class BadgeRegistry {
	/**
	 * Badges by player.
	 *
	 * <p>Concurrent because it is written from the network thread when a relay arrives and
	 * read from the render thread every frame, and a nametag is drawn far too often to be
	 * worth a lock.
	 */
	private static final Map<UUID, Badge> BADGES = new ConcurrentHashMap<>();

	private BadgeRegistry() {
	}

	/**
	 * A mark shown beside a name.
	 *
	 * @param id stable identifier, so a later source can replace an entry rather than
	 *     adding a second one for the same thing
	 * @param tooltip what it means, shown when the player list is open
	 */
	public record Badge(String id, String tooltip) {
	}

	/** The badge for using the launcher, which is the only one so far. */
	public static final Badge LOADOUT = new Badge("loadout", "Playing with Loadout");

	public static Optional<Badge> forPlayer(UUID player) {
		return Optional.ofNullable(BADGES.get(player));
	}

	public static boolean has(UUID player) {
		return BADGES.containsKey(player);
	}

	/**
	 * Records that a player has a badge.
	 *
	 * <p>Called for the local player at startup, and for others only when something
	 * trustworthy said so. Nothing here infers a badge from behaviour: guessing who is
	 * using which launcher from what their client does is both unreliable and not a
	 * thing this should be doing.
	 */
	public static void grant(UUID player, Badge badge) {
		BADGES.put(player, badge);
	}

	public static void revoke(UUID player) {
		BADGES.remove(player);
	}

	/**
	 * Forgets everyone.
	 *
	 * <p>Called on disconnect. Badges are per-session knowledge -- who was on that server
	 * at that time -- and carrying them into the next server would show marks for players
	 * who are not there.
	 */
	public static void clear() {
		BADGES.clear();
	}

	public static Map<UUID, Badge> all() {
		return Collections.unmodifiableMap(BADGES);
	}
}
