package turbo.pos.boost.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class JtlParserUtil {

    @Data
    @Builder
    public static class JtlMetrics {
        private long samples;
        private double avgMs;
        private int p95Ms;
        private double throughputRps;
        private double errorPct;
    }

    public static JtlMetrics parse(String filePath, String labelFilter) {
        List<Integer> elapsedTimes = new ArrayList<>();
        long ok = 0;
        long fail = 0;
        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return emptyMetrics();

            String[] headers = headerLine.split(",");
            int idxTs = findIndex(headers, "timeStamp");
            int idxElapsed = findIndex(headers, "elapsed");
            int idxLabel = findIndex(headers, "label");
            int idxSuccess = findIndex(headers, "success");

            String line;
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length <= Math.max(idxTs, Math.max(idxElapsed, idxSuccess))) continue;

                // Filter by label if provided (e.g., "POST Platform", "POST Virtual")
                if (labelFilter != null && idxLabel >= 0) {
                    if (!columns[idxLabel].contains(labelFilter)) continue;
                }

                long ts = Long.parseLong(columns[idxTs]);
                int elapsed = Integer.parseInt(columns[idxElapsed]);
                boolean success = "true".equalsIgnoreCase(columns[idxSuccess]);

                elapsedTimes.add(elapsed);
                if (success) ok++; else fail++;
                minTs = Math.min(minTs, ts);
                maxTs = Math.max(maxTs, ts + elapsed);
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Error parsing JTL file: {}", filePath, e);
            return emptyMetrics();
        }

        if (elapsedTimes.isEmpty()) return emptyMetrics();

        Collections.sort(elapsedTimes);
        long sum = 0;
        for (int t : elapsedTimes) sum += t;

        double durationSec = (maxTs - minTs) / 1000.0;
        long total = ok + fail;

        return JtlMetrics.builder()
                .samples(total)
                .avgMs(round((double) sum / total))
                .p95Ms(percentile(elapsedTimes, 0.95))
                .throughputRps(durationSec > 0 ? round(total / durationSec) : 0)
                .errorPct(round((double) fail * 100.0 / total))
                .build();
    }

    private static int findIndex(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static int percentile(List<Integer> sorted, double p) {
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static JtlMetrics emptyMetrics() {
        return JtlMetrics.builder().samples(0).avgMs(0).p95Ms(0).throughputRps(0).errorPct(0).build();
    }
}
