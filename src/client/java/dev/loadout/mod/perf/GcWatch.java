package dev.loadout.mod.perf;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

/**
 * What the garbage collector has been doing, and how long it took.
 *
 * <h2>What this is for</h2>
 *
 * <p>The launcher offers collector presets -- a tuned G1, and ZGC for low pauses. Those are
 * claims, and until somebody can see the numbers on their own machine with their own pack
 * they stay claims. This is the half of the evidence that names the cause; {@link
 * FrameTimes} is the half that says whether it mattered.
 *
 * <h2>What a duration here does and does not mean</h2>
 *
 * <p>A collection's duration is not the same thing as a pause. G1's young collections stop
 * the world for very nearly their whole duration, so for those the two are close enough to
 * read as one. ZGC does almost all of its work concurrently, so its durations can be long
 * while nothing stopped at all -- which is the entire point of it, and why a big number
 * here next to steady frame times is a success rather than a problem.
 *
 * <p>So: this says what ran and for how long, and the frame times say whether anybody felt
 * it. Reporting only one of the two would be misleading in one direction or the other.
 */
public final class GcWatch {
	private final List<GarbageCollectorMXBean> beans;

	/**
	 * Written from the collector's own notification threads.
	 *
	 * <p>Atomics rather than a lock: these are updated during a collection, and blocking
	 * there to keep an overlay tidy would be a poor trade.
	 */
	private final AtomicLong collections = new AtomicLong();
	private final AtomicLong totalMs = new AtomicLong();
	private final AtomicLong worstMs = new AtomicLong();

	private final List<Runnable> unsubscribe = new ArrayList<>();
	/** Moves on a reset, so the rate is always per minute of the window being measured. */
	private volatile long since = System.nanoTime();

	public GcWatch() {
		this.beans = List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
	}

	/** The collectors actually in use, which is how a preset is confirmed to have applied. */
	public String collectorNames() {
		List<String> names = new ArrayList<>();
		for (GarbageCollectorMXBean bean : this.beans) {
			names.add(bean.getName());
		}
		return names.isEmpty() ? "unknown" : String.join(" + ", names);
	}

	/**
	 * Starts listening for individual collections.
	 *
	 * <p>Notifications rather than polling the cumulative counters, because the number worth
	 * having is the worst single collection and a running total cannot produce one.
	 *
	 * @return false if this JVM does not emit them, in which case only the totals are real
	 */
	public boolean start() {
		boolean any = false;

		for (GarbageCollectorMXBean bean : this.beans) {
			if (!(bean instanceof NotificationEmitter emitter)) {
				continue;
			}

			NotificationListener listener = (notification, handback) -> {
				// Matched by type name rather than by casting to the com.sun composite
				// type: this only needs the duration, and reading it out of the CompositeData
				// avoids depending on a class that is not part of the Java SE API.
				if (!"com.sun.management.gc.notification".equals(notification.getType())) {
					return;
				}
				record(durationOf(notification.getUserData()));
			};

			emitter.addNotificationListener(listener, null, null);
			this.unsubscribe.add(() -> {
				try {
					emitter.removeNotificationListener(listener);
				} catch (Exception e) {
					// Already gone, or the bean was replaced. Nothing to do about it and
					// nothing that goes wrong if it stays.
				}
			});
			any = true;
		}

		return any;
	}

	public void stop() {
		this.unsubscribe.forEach(Runnable::run);
		this.unsubscribe.clear();
	}

	private void record(long durationMs) {
		if (durationMs < 0) {
			return;
		}

		this.collections.incrementAndGet();
		this.totalMs.addAndGet(durationMs);
		this.worstMs.accumulateAndGet(durationMs, Math::max);
	}

	/**
	 * Digs the duration out of a GC notification without naming com.sun types.
	 *
	 * <p>The payload is a CompositeData with a "gcInfo" member that has a "duration". Read
	 * reflectively through the javax.management interfaces, which every JVM has.
	 */
	private static long durationOf(Object userData) {
		if (!(userData instanceof javax.management.openmbean.CompositeData data)) {
			return -1;
		}

		try {
			Object info = data.get("gcInfo");
			if (info instanceof javax.management.openmbean.CompositeData gcInfo) {
				Object duration = gcInfo.get("duration");
				if (duration instanceof Number number) {
					return number.longValue();
				}
			}
		} catch (RuntimeException e) {
			// A JVM that shapes the notification differently. The totals still work.
			return -1;
		}
		return -1;
	}

	/**
	 * @param msPerMinute how much of every minute the collector spent working -- the figure
	 *     that says whether the heap is comfortable or being fought over
	 */
	public record Summary(long collections, long totalMs, long worstMs, double msPerMinute) {
	}

	public Summary summary() {
		long elapsedNs = Math.max(System.nanoTime() - this.since, 1);
		double minutes = elapsedNs / 60_000_000_000.0;

		long total = this.totalMs.get();
		return new Summary(this.collections.get(), total, this.worstMs.get(),
				minutes <= 0 ? 0 : total / minutes);
	}

	/** Forgets what has happened so far, so a change can be measured from now. */
	public void reset() {
		this.collections.set(0);
		this.totalMs.set(0);
		this.worstMs.set(0);
		// Last, and the reason it is here at all: leaving the window start where it was
		// would divide a fresh total by the whole session and report a rate near zero.
		this.since = System.nanoTime();
	}
}
