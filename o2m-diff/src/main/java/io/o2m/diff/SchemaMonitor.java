package io.o2m.diff;

import io.o2m.core.json.JsonSupport;
import io.o2m.model.DiffChange;
import io.o2m.model.DiffResult;
import io.o2m.model.DiffSeverity;
import io.o2m.model.SchemaSnapshot;
import io.o2m.spi.SchemaComparator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class SchemaMonitor {
    private final SchemaComparator comparator;

    public SchemaMonitor(SchemaComparator comparator) {
        this.comparator = comparator;
    }

    public DiffResult compare(SchemaSnapshot expected, SchemaSnapshot actual) {
        return comparator.compare(expected, actual);
    }

    public void writeReports(DiffResult result, Path reportsDir) throws Exception {
        Files.createDirectories(reportsDir);
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
        Path json = reportsDir.resolve("diff-" + ts + ".json");
        JsonSupport.mapper().writeValue(json.toFile(), result);

        StringBuilder md = new StringBuilder("# Schema Diff Report\n\n");
        if (result.changes().isEmpty()) {
            md.append("No differences.\n");
        } else {
            for (DiffChange c : result.changes()) {
                md.append("- **").append(c.severity()).append("** `").append(c.table())
                        .append("` ").append(c.level()).append("/").append(c.name())
                        .append(" ").append(c.field()).append(": `").append(c.expected())
                        .append("` → `").append(c.actual()).append("` (").append(c.code()).append(")\n");
            }
        }
        Files.writeString(reportsDir.resolve("diff-" + ts + ".md"), md.toString());
    }

    public void writeVerifyReport(DiffResult result, Path reportsDir, boolean passed) throws Exception {
        Files.createDirectories(reportsDir);
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
        var report = new VerifyReport(passed, result.changes().size(),
                result.changes().stream().filter(c -> c.severity() == DiffSeverity.ERROR).count());
        Path json = reportsDir.resolve("verify-" + ts + ".json");
        JsonSupport.mapper().writeValue(json.toFile(), report);
    }

}
