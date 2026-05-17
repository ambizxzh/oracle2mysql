package io.o2m.migrate;

import io.o2m.core.json.JsonSupport;
import io.o2m.model.SchemaSnapshot;
import io.o2m.spi.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FileSnapshotStore implements SnapshotStore {
    @Override
    public void save(SchemaSnapshot snapshot, Path baseDir) {
        try {
            Path dir = baseDir.resolve("snapshots").resolve(snapshot.versionTag());
            Files.createDirectories(dir);
            JsonSupport.writeSnapshot(snapshot, dir.resolve("canonical.json"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save snapshot", e);
        }
    }

    @Override
    public Optional<SchemaSnapshot> load(Path baseDir, String versionTag) throws Exception {
        Path file = baseDir.resolve("snapshots").resolve(versionTag).resolve("canonical.json");
        if (!Files.exists(file)) return Optional.empty();
        return Optional.of(JsonSupport.readSnapshot(file));
    }
}
