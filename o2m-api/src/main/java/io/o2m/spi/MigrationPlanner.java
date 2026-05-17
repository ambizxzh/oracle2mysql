package io.o2m.spi;

import io.o2m.model.MigrationPlan;
import io.o2m.model.SchemaSnapshot;

public interface MigrationPlanner {
    MigrationPlan plan(SchemaSnapshot from, SchemaSnapshot to);
}
