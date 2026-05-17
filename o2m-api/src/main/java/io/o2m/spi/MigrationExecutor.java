package io.o2m.spi;

import java.nio.file.Path;
import java.util.List;

public interface MigrationExecutor {
    void ensureHistoryTable() throws Exception;

    void applyScripts(List<Path> scripts, boolean dryRun) throws Exception;

    boolean isApplied(String version) throws Exception;
}
