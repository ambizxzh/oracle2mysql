package io.o2m.spi;

import io.o2m.model.SchemaSnapshot;

import java.nio.file.Path;
import java.util.List;

public interface DdlParser {
    SchemaSnapshot parse(Path ddlFileOrDir, String schemaName, String versionTag) throws Exception;

    default SchemaSnapshot parseString(String ddl, String schemaName, String tableName) throws Exception {
        throw new UnsupportedOperationException();
    }
}
