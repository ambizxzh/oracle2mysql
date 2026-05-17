package io.o2m.migrate;

import io.o2m.core.config.MigrationConfig;
import io.o2m.diff.DefaultSchemaComparator;
import io.o2m.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMigrationPlannerTest {
    @Test
    void newTableProducesChange() {
        TableMetadata t2 = TableMetadata.builder("HR", "NEW_T")
                .column(ColumnMetadata.builder("ID").mysqlType("BIGINT").build())
                .build();
        SchemaSnapshot from = new SchemaSnapshot("o", "HR", "v1", Instant.now(), List.of());
        SchemaSnapshot to = new SchemaSnapshot("o", "HR", "v2", Instant.now(), List.of(t2));
        MigrationPlan plan = new DefaultMigrationPlanner(new DefaultSchemaComparator(), new MigrationConfig())
                .plan(from, to);
        assertFalse(plan.changes().isEmpty());
    }
}
