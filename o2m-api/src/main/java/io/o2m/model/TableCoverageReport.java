package io.o2m.model;

import java.util.List;

public record TableCoverageReport(String table, List<CoverageItem> items, boolean passed) {
    public TableCoverageReport {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public boolean hasBlockers() {
        return items.stream().anyMatch(i -> i.status() == CoverageStatus.BLOCKER || i.status() == CoverageStatus.MISSING);
    }
}
