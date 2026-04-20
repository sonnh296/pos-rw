package turbo.pos.boost.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TestRunSummaryService {

	// JMETER_RESULT_DIR / jmeter.result.dir, rồi jmeter/results, boost/jmeter/results, dev path.
	private static final List<Path> RESULT_DIR_CANDIDATES = buildResultDirCandidates();

	private static List<Path> buildResultDirCandidates() {
		List<Path> out = new ArrayList<>();
		String env = System.getenv("JMETER_RESULT_DIR");
		if (env != null && !env.isBlank()) out.add(Paths.get(env));
		String sys = System.getProperty("jmeter.result.dir");
		if (sys != null && !sys.isBlank()) out.add(Paths.get(sys));
		out.add(Paths.get("jmeter", "results"));
		out.add(Paths.get("boost", "jmeter", "results"));
		out.add(Paths.get("/Users/mac2019/Desktop/pos/boost/jmeter/results"));
		return List.copyOf(out);
	}

	private final RewardBalanceQueryService rewardBalanceQueryService;

	@Value("${demo.hotspot.customer-id:customer-race-001}")
	private String hotspotCustomerId;

	// Hotspot Phase 1 — khớp boost/jmeter/inputs/p1-*-hotspot.csv.
	@Value("${demo.hotspot.p1-a:customer-race-a}")
	private String hotspotP1A;

	@Value("${demo.hotspot.p1-b:customer-race-b}")
	private String hotspotP1B;

	@Value("${demo.hotspot.p1-c:customer-race-c}")
	private String hotspotP1C;

	// Hotspot Phase 2 — khớp boost/jmeter/inputs/p2-*-hotspot.csv (cùng kiểu tải p1).
	@Value("${demo.hotspot.p2-a:customer-lock-a}")
	private String hotspotP2A;

	@Value("${demo.hotspot.p2-b:customer-lock-b}")
	private String hotspotP2B;

	@Value("${demo.hotspot.p2-c:customer-lock-c}")
	private String hotspotP2C;

	@Value("${demo.idempotency.customer-id:customer-idempotency-001}")
	private String idempotencyCustomerId;
	private static final List<TestCaseDef> TEST_CASES = List.of(
			new TestCaseDef(
					"p1-a-single-no-lock",
					"p1",
					"Phase 1 - No-lock race condition",
					"Single no-lock (baseline)",
					"Ít hoặc không có sai lệch dữ liệu",
					"So với expected points của hotspot: thường gần đúng"),
			new TestCaseDef(
					"p1-b-platform-no-lock",
					"p1",
					"Phase 1 - No-lock race condition",
					"Platform no-lock",
					"Có khả năng sai lệch dữ liệu (race)",
					"Expected: actual points hotspot < expected points"),
			new TestCaseDef(
					"p1-c-virtual-no-lock",
					"p1",
					"Phase 1 - No-lock race condition",
					"Virtual no-lock",
					"Có khả năng sai lệch dữ liệu (race)",
					"Expected: actual points hotspot < expected points"),
			new TestCaseDef(
					"p2-a-single-lock",
					"p2",
					"Phase 2 - Lock correctness (customerId + transactionId)",
					"Single lock",
					"Expected == Actual (customerId lock + txnId guard hoạt động)",
					"Verdict CONSISTENT, Missing = 0"),
			new TestCaseDef(
					"p2-b-platform-lock",
					"p2",
					"Phase 2 - Lock correctness (customerId + transactionId)",
					"Platform lock",
					"Expected == Actual (customerId lock + txnId guard hoạt động)",
					"Verdict CONSISTENT, Missing = 0"),
			new TestCaseDef(
					"p2-c-virtual-lock",
					"p2",
					"Phase 2 - Lock correctness (customerId + transactionId)",
					"Virtual lock",
					"Expected == Actual (customerId lock + txnId guard hoạt động)",
					"Verdict CONSISTENT, Missing = 0"),
			new TestCaseDef(
					"p3-a-platform-lock-prod",
					"p3",
					"Phase 3 - Production-like performance",
					"Platform lock (prod-like)",
					"Ổn định với throughput tốt",
					"So sánh Avg/P95/Throughput với test case còn lại trong phase"),
			new TestCaseDef(
					"p3-b-virtual-lock-prod",
					"p3",
					"Phase 3 - Production-like performance",
					"Virtual lock (prod-like)",
					"Ổn định với throughput tốt hơn/ít nhất tương đương",
					"So sánh Avg/P95/Throughput với test case còn lại trong phase"),
			new TestCaseDef(
					"p4-a-idempotency",
					"p4",
					"Phase 4 - Idempotency",
					"Idempotency same-txnId (virtual lock)",
					"Chỉ cộng điểm 1 lần dù gửi nhiều request cùng transactionId",
					"Actual points == idempotencyAmount × 10 (1 lần); các request sau trả DUPLICATE_TRANSACTION"),
			new TestCaseDef(
					"p5-soak-virtual-lock",
					"p5",
					"Phase 5 - Soak / Endurance",
					"Virtual lock 30 phút",
					"P95/P99 không trôi, error rate thấp, không rò memory/connection",
					"So sánh P95 đoạn đầu vs đoạn cuối (JMeter HTML dashboard)"),
			new TestCaseDef(
					"p6-a-stress-100",
					"p6",
					"Phase 6 - Stress step load",
					"Stress 100 users",
					"Baseline performance cho stress curve",
					"Ghi lại throughput, P95 làm mốc cho các bước sau"),
			new TestCaseDef(
					"p6-b-stress-500",
					"p6",
					"Phase 6 - Stress step load",
					"Stress 500 users",
					"Throughput còn tuyến tính so với 100 users",
					"Tìm knee-point: nếu throughput giảm/latency tăng mạnh => gần giới hạn"),
			new TestCaseDef(
					"p6-c-stress-1000",
					"p6",
					"Phase 6 - Stress step load",
					"Stress 1000 users",
					"Hệ thống có thể degrade nhưng không lỗi cascade",
					"Xem error %; nếu > 5% thì đây là mức quá tải"),
			new TestCaseDef(
					"p6-d-stress-2000",
					"p6",
					"Phase 6 - Stress step load",
					"Stress 2000 users",
					"Ngưỡng break; circuit breaker/timeout phải ngăn cascade",
					"Error expected; điểm quan trọng: không bị treo, restart là phục hồi"),
			new TestCaseDef(
					"p7-chaos",
					"p7",
					"Phase 7 - Chaos / Availability",
					"Chaos Redis down + MySQL fallback",
					"Availability được giữ nhờ circuit breaker -> MySQL; không lỗi 5xx hàng loạt",
					"Error < 5%; sau test /consistency/global verdict=CONSISTENT"));

	public Map<String, Object> latestSummary() {
		Path latest = latestJtl();
		if (latest == null) {
			return Map.of(
					"hasData", false,
					"message", "Chưa tìm thấy file .jtl",
					"searchedDirs", RESULT_DIR_CANDIDATES.stream().map(Path::toString).toList());
		}
		return summarizeJtl(latest);
	}

	public Map<String, Object> listSummaries(int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 100));
		List<Map<String, Object>> rows = allJtls()
				.sorted(Comparator.comparing(TestRunSummaryService::lastModifiedSafe).reversed())
				.limit(safeLimit)
				.map(TestRunSummaryService::summarizeJtl)
				.toList();
		return Map.of(
				"count", rows.size(),
				"rows", rows);
	}

	/** Gom từng test case vào phase (LinkedHashMap giữ thứ tự phase p1, p2, …). */
	public Map<String, Object> phaseSummaries() {
		Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
		for (TestCaseDef testCase : TEST_CASES) {
			Map<String, Object> row = new HashMap<>(summaryByFileName(testCase.fileName()));
			row.put("testCaseKey", testCase.key());
			row.put("testCaseLabel", testCase.label());
			row.put("expectedResult", testCase.expectedResult());
			row.put("validationGuide", testCase.validationGuide());

			if ("p1".equals(testCase.phaseKey()) && Boolean.TRUE.equals(row.get("hasData"))) {
				attachRaceCheck(row, hotspotCustomerIdFor(testCase.key()));
			}
			if ("p2".equals(testCase.phaseKey()) && Boolean.TRUE.equals(row.get("hasData"))) {
				// p2: cùng race-check p1, hotspot theo case (lock-a/b/c).
				attachRaceCheck(row, hotspotCustomerIdFor(testCase.key()));
				attachGlobalConsistencyCheck(row);
			}
			if ("p4".equals(testCase.phaseKey()) && Boolean.TRUE.equals(row.get("hasData"))) {
				attachIdempotencyCheck(row);
			}
			if (Boolean.TRUE.equals(row.get("hasData"))
					&& (List.of("p3", "p5", "p6", "p7").contains(testCase.phaseKey()))) {
				attachGlobalConsistencyCheck(row);
			}

			Map<String, Object> phase = buckets.computeIfAbsent(testCase.phaseKey(), ignored -> {
				Map<String, Object> out = new HashMap<>();
				out.put("phaseKey", testCase.phaseKey());
				out.put("phaseTitle", testCase.phaseTitle());
				out.put("rows", new ArrayList<Map<String, Object>>());
				return out;
			});
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rows = (List<Map<String, Object>>) phase.get("rows");
			rows.add(row);
		}
		return Map.of("phases", new ArrayList<>(buckets.values()));
	}

	private void attachRaceCheck(Map<String, Object> row, String customerId) {
		// expected:{id} = INCRBY mỗi SUCCESS; độc lập RMW → so với điểm thực tế.
		Long expectedRaw = rewardBalanceQueryService.getExpectedPointsOrNull(customerId);
		boolean redisUnavailable = expectedRaw == null;
		long expected = redisUnavailable
				? rewardBalanceQueryService.getExpectedPointsFromMysqlLedger(customerId)
				: expectedRaw;
		long actual = redisUnavailable
				? rewardBalanceQueryService.getMysqlBalance(customerId)
				: readActualHotspotPoints(customerId);
		long diff = expected - actual;

		String verdict;
		boolean raceDetected;
		boolean dirtyData;
		if (redisUnavailable) {
			// MySQL-only metrics: không set raceDetected (RACE_DETECTED chỉ khi so Redis vs expected).
			// Fallback MySQL: ledger có thể chậm hơn Redis+outbox.
			if (actual < expected) {
				verdict = "UNDER_APPLIED";
				raceDetected = false;
				dirtyData = false;
			} else if (actual > expected) {
				verdict = "DIRTY_DATA";
				raceDetected = false;
				dirtyData = true;
			} else {
				verdict = "CONSISTENT";
				raceDetected = false;
				dirtyData = false;
			}
		} else if (actual < expected) {
			verdict = "RACE_DETECTED";
			raceDetected = true;
			dirtyData = false;
		} else if (actual > expected) {
			verdict = "DIRTY_DATA";
			raceDetected = false;
			dirtyData = true;
		} else {
			verdict = "CONSISTENT";
			raceDetected = false;
			dirtyData = false;
		}

		row.put("hotspotCustomerId", customerId);
		row.put("expectedPoints", expected);
		row.put("actualPoints", actual);
		row.put("pointsDiff", diff);
		row.put("expectedSource", redisUnavailable ? "mysql" : "redis");
		row.put("actualSource", redisUnavailable ? "mysql" : "primary");
		row.put("raceDetected", raceDetected);
		row.put("dirtyData", dirtyData);
		row.put("verdict", verdict);
	}

	private void attachIdempotencyCheck(Map<String, Object> row) {
		// Chỉ SUCCESS đầu bump expected/points; DUPLICATE không bump.
		Long expectedRaw = rewardBalanceQueryService.getExpectedPointsOrNull(idempotencyCustomerId);
		boolean redisUnavailable = expectedRaw == null;
		long expected = redisUnavailable
				? rewardBalanceQueryService.getExpectedPointsFromMysqlLedger(idempotencyCustomerId)
				: expectedRaw;
		long actual = redisUnavailable
				? rewardBalanceQueryService.getMysqlBalance(idempotencyCustomerId)
				: readActualHotspotPoints(idempotencyCustomerId);
		long samples = toLong(row.get("samples"));
		long ok = toLong(row.get("ok"));
		long duplicates = Math.max(0L, samples - ok);

		// samples = tổng request .jtl; ok = số SUCCESS (200). Kỳ vọng idempotent: samples>1, ok==1, actual==expected.
		String verdict;
		if (actual == 0 && expected == 0) {
			verdict = "UNDER_APPLIED";
		} else if (actual > expected) {
			verdict = "DIRTY_DATA";
		} else if (actual < expected) {
			verdict = "UNDER_APPLIED";
		} else if (samples > 1 && ok == 1) {
			verdict = "IDEMPOTENT_OK";
		} else if (samples > 1 && ok > 1) {
			verdict = "IDEMPOTENCY_VIOLATED";
		} else {
			verdict = "INCONCLUSIVE";
		}

		row.put("idempotencyCustomerId", idempotencyCustomerId);
		row.put("expectedPoints", expected);
		row.put("actualPoints", actual);
		row.put("expectedDuplicates", duplicates);
		row.put("successfulApplies", ok);
		row.put("expectedSource", redisUnavailable ? "mysql" : "redis");
		row.put("actualSource", redisUnavailable ? "mysql" : "primary");
		row.put("verdict", verdict);
	}

	private void attachGlobalConsistencyCheck(Map<String, Object> row) {
		try {
			Map<String, Object> global = rewardBalanceQueryService.globalConsistencyReport();
			row.put("globalConsistency", global);
		} catch (Exception ignored) {
		}
	}

	private String hotspotCustomerIdFor(String testCaseKey) {
		return switch (testCaseKey) {
			case "p1-a-single-no-lock" -> hotspotP1A;
			case "p1-b-platform-no-lock" -> hotspotP1B;
			case "p1-c-virtual-no-lock" -> hotspotP1C;
			case "p2-a-single-lock" -> hotspotP2A;
			case "p2-b-platform-lock" -> hotspotP2B;
			case "p2-c-virtual-lock" -> hotspotP2C;
			default -> hotspotCustomerId;
		};
	}

	private long readActualHotspotPoints(String customerId) {
		try {
			Object points = rewardBalanceQueryService.getPrimaryPoints(customerId).get("points");
			return toLong(points);
		} catch (Exception e) {
			return 0L;
		}
	}

	private static long toLong(Object value) {
		if (value instanceof Number n) return n.longValue();
		if (value == null) return 0L;
		try {
			return Long.parseLong(value.toString());
		} catch (Exception e) {
			return 0L;
		}
	}

	private static Path latestJtl() {
		return allJtls()
				.max(Comparator.comparing(TestRunSummaryService::lastModifiedSafe))
				.orElse(null);
	}

	private static Map<String, Object> summaryByFileName(String fileName) {
		Path matched = allJtls()
				.filter(path -> path.getFileName().toString().equals(fileName))
				.max(Comparator.comparing(TestRunSummaryService::lastModifiedSafe))
				.orElse(null);
		if (matched == null) {
			return Map.of(
					"hasData", false,
					"fileName", fileName,
					"message", "Chưa có dữ liệu cho test case này");
		}
		return summarizeJtl(matched);
	}

	private static Stream<Path> allJtls() {
		return RESULT_DIR_CANDIDATES.stream()
				.distinct()
				.filter(Files::isDirectory)
				.flatMap(TestRunSummaryService::safeList)
				.filter(p -> p.getFileName().toString().endsWith(".jtl"));
	}

	/** Xoá *.jtl trong các thư mục kết quả (JMeter append — cần xoá trước run mới). @return số file đã xoá. */
	public static long deleteAllJtlFiles() {
		long deleted = 0;
		for (Path dir : RESULT_DIR_CANDIDATES.stream().distinct().toList()) {
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> stream = Files.list(dir)) {
				for (Path p : stream.toList()) {
					if (!p.getFileName().toString().endsWith(".jtl")) continue;
					try {
						Files.deleteIfExists(p);
						deleted++;
					} catch (IOException ignored) {
					}
				}
			} catch (IOException ignored) {
			}
		}
		return deleted;
	}

	private static Stream<Path> safeList(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.toList().stream();
		} catch (IOException e) {
			return Stream.empty();
		}
	}

	private static FileTime lastModifiedSafe(Path p) {
		try {
			return Files.getLastModifiedTime(p);
		} catch (IOException e) {
			return FileTime.fromMillis(0);
		}
	}

	private static Map<String, Object> summarizeJtl(Path file) {
		List<Integer> elapsed = new ArrayList<>();
		long ok = 0;
		long fail = 0;
		long minStartTs = Long.MAX_VALUE;
		long maxEndTs = Long.MIN_VALUE;

		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			if (lines.size() <= 1) {
				return Map.of(
						"hasData", false,
						"fileName", file.getFileName().toString(),
						"message", "File .jtl rỗng hoặc chưa có sample");
			}

			List<String> header = parseCsvLine(lines.get(0));
			int idxTs = header.indexOf("timeStamp");
			int idxElapsed = header.indexOf("elapsed");
			int idxSuccess = header.indexOf("success");
			if (idxTs < 0 || idxElapsed < 0 || idxSuccess < 0) {
				return Map.of(
						"hasData", false,
						"fileName", file.getFileName().toString(),
						"message", "Header .jtl không đúng định dạng");
			}

			// JMeter CSV: cột theo tên, thứ tự cột có thể khác giữa các phiên bản/plugin.
			for (int i = 1; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line == null || line.isBlank()) continue;
				List<String> row = parseCsvLine(line);
				if (row.size() <= Math.max(idxTs, Math.max(idxElapsed, idxSuccess))) continue;

				long ts = parseLong(row.get(idxTs), 0L);
				int ms = (int) parseLong(row.get(idxElapsed), 0L);
				boolean success = "true".equalsIgnoreCase(row.get(idxSuccess));

				elapsed.add(ms);
				if (success) ok++;
				else fail++;

				minStartTs = Math.min(minStartTs, ts);
				// Throughput window: end time = timeStamp + elapsed (start-only would skew RPS).
				maxEndTs = Math.max(maxEndTs, ts + ms);
			}
		} catch (IOException e) {
			return Map.of(
					"hasData", false,
					"fileName", file.getFileName().toString(),
					"message", "Không đọc được file .jtl: " + e.getMessage());
		}

		long total = ok + fail;
		if (total == 0) {
			return Map.of(
					"hasData", false,
					"fileName", file.getFileName().toString(),
					"message", "Chưa có sample trong file .jtl");
		}

		elapsed.sort(Integer::compareTo);
		long sum = 0;
		int max = 0;
		for (int v : elapsed) {
			sum += v;
			max = Math.max(max, v);
		}

		double avg = (double) sum / total;
		int p95 = percentile(elapsed, 0.95);
		int p99 = percentile(elapsed, 0.99);
		double errorPct = ((double) fail * 100.0) / total;

		// RPS = tổng sample / (thời điểm kết thúc muộn nhất − bắt đầu sớm nhất), đã dùng end = start+elapsed ở trên.
		double durationSec = (maxEndTs > minStartTs) ? (maxEndTs - minStartTs) / 1000.0 : 0.0;
		double throughput = durationSec > 0 ? total / durationSec : 0.0;

		Map<String, Object> out = new HashMap<>();
		out.put("hasData", true);
		out.put("fileName", file.getFileName().toString());
		out.put("filePath", file.toAbsolutePath().toString());
		out.put("runTag", extractRunTag(file));
		out.put("modifiedAt", modifiedAtIso(file));
		out.put("samples", total);
		out.put("ok", ok);
		out.put("fail", fail);
		out.put("errorPct", round2(errorPct));
		out.put("avgMs", round2(avg));
		out.put("p95Ms", p95);
		out.put("p99Ms", p99);
		out.put("maxMs", max);
		out.put("throughputRps", round2(throughput));
		out.put("startedAt", minStartTs == Long.MAX_VALUE ? null : Instant.ofEpochMilli(minStartTs).toString());
		out.put("endedAt", maxEndTs == Long.MIN_VALUE ? null : Instant.ofEpochMilli(maxEndTs).toString());
		return out;
	}

	private static String extractRunTag(Path file) {
		String name = file.getFileName().toString();
		if (!name.endsWith(".jtl")) return name;
		String base = name.substring(0, name.length() - 4);
		return base.startsWith("run-") ? base.substring(4) : base;
	}

	private record TestCaseDef(
			String key,
			String phaseKey,
			String phaseTitle,
			String label,
			String expectedResult,
			String validationGuide) {
		private String fileName() {
			return key + ".jtl";
		}
	}

	private static String modifiedAtIso(Path file) {
		try {
			return Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString();
		} catch (IOException e) {
			return null;
		}
	}

	/** nearest-rank trên danh sách đã sort (đủ cho dashboard demo, không nội suy giữa hai mẫu). */
	private static int percentile(List<Integer> sorted, double p) {
		if (sorted.isEmpty()) return 0;
		int idx = (int) Math.floor((sorted.size() - 1) * p);
		return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
	}

	private static long parseLong(String raw, long fallback) {
		try {
			return Long.parseLong(raw);
		} catch (Exception e) {
			return fallback;
		}
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	/** Tách CSV JMeter: hỗ trợ field bọc ngoặc kép và "" escape trong quoted field. */
	private static List<String> parseCsvLine(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					sb.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (c == ',' && !inQuotes) {
				out.add(sb.toString());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		out.add(sb.toString());
		return out;
	}
}
