package turbo.pos.boost.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/test-results")
@Slf4j
public class TestResultController {

    private File getProjectRoot() {
        try {
            File projectRoot = new File(System.getProperty("user.dir")).getParentFile();
            if (projectRoot != null && projectRoot.getName().equals("boost")) {
                projectRoot = projectRoot.getParentFile();
            } else if (projectRoot != null && !projectRoot.getName().equals("pos") && new File(System.getProperty("user.dir")).getName().equals("pos")) {
                projectRoot = new File(System.getProperty("user.dir"));
            } else if (new File("../loadtest/results_csv").exists()) {
                projectRoot = new File("..").getCanonicalFile();
            }
            return projectRoot;
        } catch (Exception e) {
            return new File(".");
        }
    }

    @GetMapping("/csv/{phase}")
    public ResponseEntity<String> getCsvResult(@PathVariable String phase) {
        try {
            File root = getProjectRoot();
            File csvFile;
            if ("phase1".equalsIgnoreCase(phase)) {
                csvFile = new File(root, "loadtest/results_csv/phase1_results.csv");
            } else if ("phase2".equalsIgnoreCase(phase)) {
                csvFile = new File(root, "loadtest/results_csv/phase2_results.csv");
            } else {
                return ResponseEntity.badRequest().body("Invalid phase");
            }

            if (!csvFile.exists()) {
                return ResponseEntity.ok("");
            }

            String content = Files.readString(Path.of(csvFile.getAbsolutePath()));
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.error("Error reading CSV file", e);
            return ResponseEntity.internalServerError().body("Error reading CSV file");
        }
    }

    @DeleteMapping("/csv")
    public Map<String, String> clearResults() {
        try {
            File root = getProjectRoot();
            File p1 = new File(root, "loadtest/results_csv/phase1_results.csv");
            File p2 = new File(root, "loadtest/results_csv/phase2_results.csv");
            
            if (p1.exists()) p1.delete();
            if (p2.exists()) p2.delete();
            
            return Map.of("status", "success", "message", "Cleared custom test results.");
        } catch (Exception e) {
            log.error("Error deleting CSV files", e);
            return Map.of("status", "error", "message", "Could not clear files");
        }
    }
}
