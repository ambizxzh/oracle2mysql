package io.o2m.model;

public record MigrationChange(String table, String sql, DiffSeverity severity) {}
