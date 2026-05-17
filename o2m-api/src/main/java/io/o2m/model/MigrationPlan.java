package io.o2m.model;

import java.util.List;

public record MigrationPlan(String fromVersion, String toVersion, List<MigrationChange> changes) {
    public MigrationPlan {
        changes = changes != null ? List.copyOf(changes) : List.of();
    }
}
