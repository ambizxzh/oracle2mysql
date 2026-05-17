package io.o2m.model;

import java.util.List;

public record DiffResult(List<DiffChange> changes) {
    public DiffResult {
        changes = changes != null ? List.copyOf(changes) : List.of();
    }

    public boolean hasErrors() {
        return changes.stream().anyMatch(c -> c.severity() == DiffSeverity.ERROR);
    }

    public boolean passed() {
        return !hasErrors();
    }
}
