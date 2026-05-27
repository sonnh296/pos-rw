package turbo.pos.boost.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thời gian từng pha trong {@code LockingRedisRewardService} (nanoseconds), thread-local khi bật diagnostics.
 */
public final class RewardPhaseTiming {

	private static final ThreadLocal<Builder> CURRENT = new ThreadLocal<>();

	private RewardPhaseTiming() {
	}

	public static void begin() {
		CURRENT.set(new Builder());
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static boolean isActive() {
		return CURRENT.get() != null;
	}

	public static void record(String phase, long nanos) {
		Builder b = CURRENT.get();
		if (b != null) {
			b.phases.put(phase, nanos);
		}
	}

	public static Map<String, Long> snapshotMillis() {
		Builder b = CURRENT.get();
		if (b == null) {
			return Map.of();
		}
		Map<String, Long> ms = new LinkedHashMap<>();
		b.phases.forEach((k, v) -> ms.put(k, v / 1_000_000L));
		return ms;
	}

	private static final class Builder {
		private final Map<String, Long> phases = new LinkedHashMap<>();
	}
}
