package io.o2m.spi;

import io.o2m.model.GeneratedDdl;
import io.o2m.model.TableMetadata;

public interface MysqlDdlGenerator {
    GeneratedDdl generate(TableMetadata table);
}
