package io.o2m.model;

public record DiffChange(
        String table,
        String level,
        String name,
        String field,
        String expected,
        String actual,
        DiffSeverity severity,
        String code
) {}
