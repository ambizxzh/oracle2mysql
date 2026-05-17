package io.o2m.spi;

import io.o2m.model.ColumnMetadata;
import io.o2m.model.TableMetadata;

public interface TypeMappingStrategy {
    ColumnMetadata mapColumn(TableMetadata table, ColumnMetadata column);
}
