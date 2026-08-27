package dev.loadout.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The running game's connection to the launcher that started it.
 *
 * <p>This is what makes changing mods from inside the game possible at all: the launcher
 * is still running, it already owns every operation, and it is listening on loopback. The
 * mod does not need its own copy of any of that -- it needs a phone line.
 *
 * <h2>What it is allowed to do</h2>
 *
 * <p>The token handed to the game is not the one the launcher's own window uses. Anything
 * in this JVM can read a system property, so whatever is put there has to be something it
 * is acceptable for every mod in the pack to hold. That token reaches a deliberately small
 * set of endpoints -- read this instance, turn things on and off, install -- and is
 * refused for accounts, settings, deleting an instance, or launching anything.
 *
 * <h2>What it cannot do</h2>
 *
 * <p>It cannot make Minecraft load a mod that is not already loaded. Fabric resolves and
 * transforms mods during bootstrap, before any of this exists; classes are loaded and
 * mixins are applied once. So installing from here changes what the *next* launch has,
 * and the honest thing is to say so rather than to appear to have done something.
 *
 * <p>Resource packs, shader packs and data packs are different -- the game reloads those
 * on demand -- which is why they are worth treating separately rather than lumping
 * everything under one word.
 */
public final class LauncherApi {
	private static final String PORT_PROPERTY = "loadout.api.port";
	private static final String TOKEN_PROPERTY = "loadout.api.token";
	private static final String INSTANCE_PROPERTY = "loadout.instance.name";

	private final int port;
	private final String token;
	private final String instance;
	private final HttpClient http;

	private LauncherApi(int port, String token, String instance) {
		this.port = port;
		this.token = token;
		this.instance = instance;
		this.http = HttpClient.newBuilder()
				// Short: the launcher is on this machine. A long timeout here would mean
				// the game hanging on a launcher that has already been closed.
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	/**
	 * The connection, if this game was started by a launcher that offered one.
	 *
	 * <p>Empty under any other launcher, or when Loadout was closed after starting the
	 * game. Both are ordinary situations, not errors.
	 */
	public static Optional<LauncherApi> available() {
		String port = System.getProperty(PORT_PROPERTY);
		String token = System.getProperty(TOKEN_PROPERTY);

		if (port == null || token == null || token.isBlank()) {
			return Optional.empty();
		}

		try {
			return Optional.of(new LauncherApi(Integer.parseInt(port), token,
					System.getProperty(INSTANCE_PROPERTY, "")));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public String instanceName() {
		return this.instance;
	}

	/** @param modId the id inside the jar, which is what the game knows a mod by */
	public record InstalledMod(String fileName, String modId, boolean enabled, String source) {
	}

	/** Whether the launcher is still there. Called before showing anything that needs it. */
	public boolean reachable() {
		try {
			get("/health");
			return true;
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	public List<InstalledMod> mods() throws IOException, InterruptedException {
		JsonObject profile = get("/profiles/" + encode(this.instance));
		JsonArray mods = profile.getAsJsonArray("mods");

		List<InstalledMod> found = new ArrayList<>();
		if (mods != null) {
			for (JsonElement element : mods) {
				JsonObject mod = element.getAsJsonObject();
				// Only mods: a resource pack lives in the same list but is not something a
				// mod list should offer to turn off.
				if (!"mod".equals(string(mod, "contentType", "mod"))) {
					continue;
				}
				found.add(new InstalledMod(
						string(mod, "fileName", ""),
						string(mod, "modId", null),
						mod.has("enabled") && mod.get("enabled").getAsBoolean(),
						string(mod, "source", null)));
			}
		}
		return List.copyOf(found);
	}

	/**
	 * Turns a mod on or off for the next launch.
	 *
	 * <p>Named for what it does. Calling this "enable" would suggest the mod becomes active
	 * now, and it does not -- Fabric decided what was loaded before this game had a window.
	 */
	public void setEnabledNextLaunch(String fileName, boolean enabled)
			throws IOException, InterruptedException {
		send("PUT", "/profiles/" + encode(this.instance) + "/mods/" + encode(fileName),
				"{\"enabled\":" + enabled + "}");
	}

	/** Starts an install. Returns the job id, which progress can be followed by. */
	public String install(String source, String modId) throws IOException, InterruptedException {
		JsonObject response = send("POST", "/profiles/" + encode(this.instance) + "/mods",
				"{\"source\":\"" + source + "\",\"id\":\"" + modId + "\"}");
		return string(response, "jobId", null);
	}

	/**
	 * Starts a fresh game with the current mod set, optionally rejoining somewhere.
	 *
	 * <p>The one endpoint here that has an effect outside the launcher's own records, and
	 * the reason the narrow token is allowed to reach it: a change that only applies next
	 * launch is worthless unless the next launch is cheap to reach.
	 *
	 * @param type "multiplayer" or "singleplayer", or null to start at the menu
	 */
	public void relaunch(String type, String target) throws IOException, InterruptedException {
		String body = type == null || target == null
				? "{}"
				: "{\"quickPlay\":{\"type\":\"" + type + "\",\"target\":\"" + target + "\"}}";

		send("POST", "/profiles/" + encode(this.instance) + "/launch", body);
	}

	public JsonObject search(String query, String type) throws IOException, InterruptedException {
		return get("/search?profile=" + encode(this.instance)
				+ "&q=" + encode(query)
				+ "&type=" + encode(type == null ? "mod" : type)
				+ "&limit=30");
	}

	// -- transport ---------------------------------------------------------------------

	private JsonObject get(String path) throws IOException, InterruptedException {
		return parse(this.http.send(request(path).GET().build(),
				HttpResponse.BodyHandlers.ofString()));
	}

	private JsonObject send(String method, String path, String body)
			throws IOException, InterruptedException {
		return parse(this.http.send(
				request(path)
						.header("Content-Type", "application/json")
						.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
						.build(),
				HttpResponse.BodyHandlers.ofString()));
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
				.header("Authorization", "Bearer " + this.token)
				.header("Accept", "application/json")
				.timeout(Duration.ofSeconds(10));
	}

	private static JsonObject parse(HttpResponse<String> response) throws IOException {
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(response.body());
		} catch (RuntimeException e) {
			throw new IOException("The launcher returned something unreadable");
		}

		JsonObject json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		if (response.statusCode() / 100 != 2) {
			// The launcher's own message is the useful one -- "not available to the running
			// game" says considerably more than 403 does.
			throw new IOException(string(json, "error", "The launcher refused that ("
					+ response.statusCode() + ")"));
		}
		return json;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String string(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
	}
}
