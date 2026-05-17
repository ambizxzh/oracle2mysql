package io.o2m.spi;

import io.o2m.model.GeneratedDdl;
import io.o2m.model.TableMetadata;

public interface OracleDdlGenerator {
    GeneratedDdl generate(TableMetadata table);
}
