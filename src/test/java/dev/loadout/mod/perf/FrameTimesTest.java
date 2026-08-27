package dev.loadout.mod.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic behind the overlay.
 *
 * <p>The whole point of the overlay is to replace claims about performance with numbers, so
 * the numbers have to be right. The one that matters most is the 1% low: it is the figure
 * somebody will use to decide whether a garbage collector setting helped, and a percentile
 * that is quietly off by a bucket would make a setting look like it worked when it did not.
 */
class FrameTimesTest {

	private static final double MS = 1_000_000.0;

	private static FrameTimes withFramesOfMs(double... millis) {
		FrameTimes times = new FrameTimes();
		for (double ms : millis) {
			times.record(Math.round(ms * MS));
		}
		return times;
	}

	@Test
	@DisplayName("nothing recorded yet reports nothing rather than dividing by zero")
	void empty() {
		FrameTimes times = new FrameTimes();

		assertTrue(times.isEmpty());
		FrameTimes.Summary summary = times.summary();
		assertEquals(0, summary.frames());
		assertEquals(0.0, summary.averageMs());
	}

	@Test
	@DisplayName("nanoseconds in, milliseconds out")
	void convertsUnits() {
		FrameTimes.Summary summary = withFramesOfMs(16.0, 16.0, 16.0).summary();

		assertEquals(16.0, summary.averageMs(), 0.001);
		assertEquals(16.0, summary.worstMs(), 0.001);
		assertEquals(3, summary.frames());
	}

	@Test
	@DisplayName("one bad frame in a hundred is invisible in the average and obvious in the tail")
	void theWholeReasonThisExists() {
		double[] frames = new double[100];
		java.util.Arrays.fill(frames, 8.0);
		frames[42] = 400.0;   // one stutter

		FrameTimes.Summary summary = withFramesOfMs(frames).summary();

		// The average barely moves -- 11.9ms, which reads as a perfectly good 84fps and is
		// exactly why an average frames-per-second cannot show a stutter.
		assertEquals(11.92, summary.averageMs(), 0.01);

		// The tail says what actually happened.
		assertEquals(400.0, summary.onePercentLowMs(), 0.01);
		assertEquals(400.0, summary.worstMs(), 0.01);
	}

	@Test
	@DisplayName("the 1% low averages the whole slow tail rather than reporting its edge")
	void averagesTheBucketNotItsBoundary() {
		// 200 frames, so the worst one percent is two of them: 10ms and 50ms.
		double[] frames = new double[200];
		java.util.Arrays.fill(frames, 5.0);
		frames[7] = 10.0;
		frames[99] = 50.0;

		FrameTimes.Summary summary = withFramesOfMs(frames).summary();

		// 30, the mean of the two. A percentile would report 10 here and throw away the
		// 50 -- which is the frame somebody actually felt, and the one a setting has to
		// move to have done anything.
		assertEquals(30.0, summary.onePercentLowMs(), 0.01);
		assertEquals(50.0, summary.worstMs(), 0.01);
		assertEquals(5.25, summary.averageMs(), 0.001);
	}

	@Test
	@DisplayName("the window rolls, so an old hitch stops being reported as a current one")
	void windowRolls() {
		FrameTimes times = new FrameTimes();

		times.record(Math.round(500 * MS));
		for (int i = 0; i < 1024; i++) {
			times.record(Math.round(8 * MS));
		}

		FrameTimes.Summary summary = times.summary();
		// A stutter from a minute ago is history, not a measurement of now. Reporting it
		// forever would make every setting look equally bad.
		assertEquals(8.0, summary.worstMs(), 0.01);
		assertEquals(1024, summary.frames());
	}

	@Test
	@DisplayName("a frame with no measurement yet is not counted as an instant one")
	void ignoresUnmeasuredFrames() {
		FrameTimes times = new FrameTimes();

		// getFrameTimeNs is zero before vanilla has timed a frame. Recording that would
		// drag the average toward zero and flatter every setting.
		times.record(0);
		times.record(-1);
		assertTrue(times.isEmpty());

		times.record(Math.round(8 * MS));
		assertFalse(times.isEmpty());
		assertEquals(1, times.summary().frames());
	}

	@Test
	@DisplayName("clearing starts the measurement over")
	void clearing() {
		FrameTimes times = withFramesOfMs(400.0, 400.0);
		times.clear();

		assertTrue(times.isEmpty());
		times.record(Math.round(8 * MS));
		// Not an average of the two: switching the overlay on means "measure from here".
		assertEquals(8.0, times.summary().averageMs(), 0.01);
	}
}
