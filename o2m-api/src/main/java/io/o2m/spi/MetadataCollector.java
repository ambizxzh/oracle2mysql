package io.o2m.spi;

import io.o2m.model.SchemaSnapshot;

import java.util.List;

public interface MetadataCollector {
    String sourceId();

    SchemaSnapshot collect(String schemaName, List<String> tableNames, String versionTag) throws Exception;
}
