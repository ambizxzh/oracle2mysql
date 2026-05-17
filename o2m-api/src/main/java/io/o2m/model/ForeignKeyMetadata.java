package io.o2m.model;

import java.util.List;

public record ForeignKeyMetadata(
        String name,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        boolean deferred
) {}
