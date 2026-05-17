package io.o2m.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record SchemaSnapshot(
        String source,
        String schemaName,
        String versionTag,
        Instant capturedAt,
        List<TableMetadata> tables
) {
    public SchemaSnapshot {
        tables = tables != null ? List.copyOf(tables) : List.of();
    }

    public Map<String, TableMetadata> tablesByName() {
        return tables.stream().collect(Collectors.toMap(
                t -> t.name().toUpperCase(),
                t -> t,
                (a, b) -> a));
    }

    public Optional<TableMetadata> findTable(String tableName) {
        return Optional.ofNullable(tablesByName().get(tableName.toUpperCase()));
    }
}
