package dev.loadout.mod.perf;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Frame times and collector activity, on screen, while the game is running.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The launcher offers memory sizes and garbage collector presets, and every one of them
 * is a claim about how the game will feel. Claims about performance are worth very little
 * without a way to check them on the machine and the pack they are being made about -- and
 * the numbers vanilla already shows are not the ones that settle it. F3 reports an average
 * frames per second, which is precisely the statistic a stutter hides inside.
 *
 * <p>So this reports the slow tail instead. The 1% low is what a hitch looks like written
 * down: change a setting, play for a few minutes, and watch whether that number moves. If
 * it does not, the setting did not help, whatever the collector's own figures say.
 *
 * <h2>Reading it</h2>
 *
 * <p>Two lines that mean different things. The frame line is the experience. The collector
 * line is a possible cause, and is deliberately printed next to the effect rather than on
 * its own -- long collections beside steady frames mean the collector is doing its work
 * without stopping anybody, which is a success and would read as a problem alone.
 *
 * <p>Off by default and drawn only when asked for. An overlay that is always on stops being
 * information and becomes decoration.
 */
public final class PerfOverlay {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("loadout", "perf");

	/** Colours chosen to be readable over anything, matching vanilla's own debug text. */
	private static final int TEXT = 0xFFE0E0E0;
	private static final int DIM = 0xFF9A9A9A;
	private static final int BACKDROP = 0x90000000;

	private static final FrameTimes FRAMES = new FrameTimes();
	private static final GcWatch GC = new GcWatch();

	private static boolean visible;
	private static boolean listening;

	private PerfOverlay() {
	}

	/** Registers the element once. It draws nothing until it is switched on. */
	public static void register() {
		HudElementRegistry.addLast(ID, PerfOverlay::draw);
	}

	public static boolean visible() {
		return visible;
	}

	/**
	 * Turns the overlay on or off.
	 *
	 * <p>Switching it on clears both windows, so what is shown is what has happened since
	 * somebody started looking rather than an average dragged down by the loading screen.
	 * That matters for the actual use: change a setting, restart, watch from a fixed point.
	 */
	public static void setVisible(boolean wanted) {
		visible = wanted;
		if (!wanted) {
			return;
		}

		FRAMES.clear();
		GC.reset();
		if (!listening) {
			// Left registered afterwards. The listener is idle when nothing collects, and
			// unsubscribing every time the overlay is hidden would throw away the very
			// history that makes the next reading meaningful.
			listening = GC.start();
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (!visible) {
			return;
		}

		// Vanilla's own measurement of the frame it just finished, rather than a timer of
		// our own around a fraction of it.
		FRAMES.record(client.getFrameTimeNs());
		if (FRAMES.isEmpty()) {
			return;
		}

		Font font = client.font;
		List<String> lines = lines(client);

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}

		// Top right, below anything vanilla puts in the corner. The left is where F3 goes
		// and where a second overlay would sit on top of it.
		int right = graphics.guiWidth() - 4;
		int left = right - width - 4;
		int top = 4;
		int lineHeight = font.lineHeight + 1;

		graphics.fill(left - 2, top - 2, right + 2, top + lines.size() * lineHeight, BACKDROP);

		int y = top;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), left, y, i == 0 ? TEXT : DIM, false);
			y += lineHeight;
		}
	}

	private static List<String> lines(Minecraft client) {
		FrameTimes.Summary frames = FRAMES.summary();
		GcWatch.Summary gc = GC.summary();

		List<String> lines = new ArrayList<>(4);

		// Average first because it is the familiar one, then the number that actually
		// answers the question.
		lines.add(String.format("%.1f ms avg  ·  %.1f ms 1%% low  ·  %.0f ms worst",
				frames.averageMs(), frames.onePercentLowMs(), frames.worstMs()));

		lines.add(String.format("%d fps  ·  %d frames measured", client.getFps(), frames.frames()));

		if (listening) {
			lines.add(String.format("%s  ·  %d collections  ·  %d ms worst  ·  %.0f ms/min",
					GC.collectorNames(), gc.collections(), gc.worstMs(), gc.msPerMinute()));
		} else {
			// Said rather than left blank: a missing line reads as "no garbage collection",
			// which would be a considerably more interesting claim than the truth.
			lines.add(GC.collectorNames() + "  ·  this JVM does not report collections");
		}

		Runtime runtime = Runtime.getRuntime();
		long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
		long maxMb = runtime.maxMemory() / (1024 * 1024);
		lines.add(String.format("%d / %d MB heap", usedMb, maxMb));

		return lines;
	}
}
