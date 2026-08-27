package dev.loadout.mod.perf;

import java.util.Arrays;

/**
 * A rolling window of frame times, and the numbers worth reading off it.
 *
 * <h2>Why not just show FPS</h2>
 *
 * <p>An average frames-per-second is the one number that cannot show a stutter. Sixty
 * frames where fifty-nine take 10ms and one takes 400ms averages out to something that
 * looks fine, and the 400ms frame is the entire problem -- it is the one a person felt.
 * So this keeps the frames themselves and reports the slow tail.
 *
 * <p>The 1% low is the measurement that settles arguments about garbage collector settings.
 * A collector pause that never shows up in it did not hurt anybody, whatever its duration
 * says; one that does is exactly what a lower pause target is for.
 *
 * <p>Not thread safe, and does not need to be: samples arrive from the render thread and
 * are read from the render thread.
 *
 * <p>Kept on the common side rather than with the rest of the overlay, because it is
 * arithmetic over an array of longs and touches nothing from the game -- which is what
 * makes it testable, and a measuring tool with untested measurements is not worth having.
 */
public final class FrameTimes {
	/**
	 * How many frames to keep.
	 *
	 * <p>About eight seconds at 120fps and sixteen at 60. Long enough that an occasional
	 * hitch is still in the window when somebody looks up at the overlay, short enough that
	 * the numbers respond when a setting changes.
	 */
	private static final int WINDOW = 1024;

	private final long[] samples = new long[WINDOW];
	private int count;
	private int next;

	/** Reused so reading the overlay every frame does not allocate an array every frame. */
	private final long[] scratch = new long[WINDOW];

	public void record(long frameTimeNs) {
		if (frameTimeNs <= 0) {
			return;   // the first frame, before vanilla has timed one
		}

		this.samples[this.next] = frameTimeNs;
		this.next = (this.next + 1) % WINDOW;
		if (this.count < WINDOW) {
			this.count++;
		}
	}

	public boolean isEmpty() {
		return this.count == 0;
	}

	/**
	 * @param averageMs the ordinary frame
	 * @param onePercentLowMs the mean of the slowest one percent -- what a stutter looks
	 *     like as a number, and the figure that moves when a setting actually helps
	 * @param worstMs the single worst frame still in the window
	 */
	public record Summary(double averageMs, double onePercentLowMs, double worstMs, int frames) {
	}

	public Summary summary() {
		if (this.count == 0) {
			return new Summary(0, 0, 0, 0);
		}

		System.arraycopy(this.samples, 0, this.scratch, 0, this.count);
		long total = 0;
		for (int i = 0; i < this.count; i++) {
			total += this.scratch[i];
		}

		// Sorting a thousand longs is a few microseconds and happens once a frame. Keeping
		// a running histogram instead would be faster and considerably harder to trust.
		Arrays.sort(this.scratch, 0, this.count);

		// The mean of the worst hundredth, not the value at the 99th percentile.
		//
		// That distinction is the whole measurement. A percentile reports the *boundary* of
		// the slow tail, so a window with ten slow frames reports the mildest of the ten and
		// the nine worse ones vanish -- one 400ms stutter in a hundred frames comes out as
		// an untroubled 8ms. Averaging the bucket keeps them, which is why benchmarks call
		// this a "1% low" rather than a percentile.
		int worstCount = Math.max(1, this.count / 100);
		long worstTotal = 0;
		for (int i = this.count - worstCount; i < this.count; i++) {
			worstTotal += this.scratch[i];
		}

		return new Summary(
				toMillis(total / (double) this.count),
				toMillis(worstTotal / (double) worstCount),
				toMillis(this.scratch[this.count - 1]),
				this.count);
	}

	public void clear() {
		this.count = 0;
		this.next = 0;
	}

	private static double toMillis(double nanos) {
		return nanos / 1_000_000.0;
	}
}
