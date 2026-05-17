package io.o2m.spi;

import io.o2m.model.SchemaSnapshot;

import java.nio.file.Path;
import java.util.Optional;

public interface SnapshotStore {
    void save(SchemaSnapshot snapshot, Path baseDir);

    Optional<SchemaSnapshot> load(Path baseDir, String versionTag) throws Exception;
}
