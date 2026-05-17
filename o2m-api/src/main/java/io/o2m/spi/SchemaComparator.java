package io.o2m.spi;

import io.o2m.model.DiffResult;
import io.o2m.model.SchemaSnapshot;

public interface SchemaComparator {
    DiffResult compare(SchemaSnapshot expected, SchemaSnapshot actual);
}
