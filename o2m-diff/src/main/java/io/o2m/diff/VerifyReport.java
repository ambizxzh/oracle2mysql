package io.o2m.diff;

public record VerifyReport(boolean passed, int totalChanges, long errorCount) {}
