package io.o2m.diff;

import io.o2m.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSchemaComparatorTest {
    private final DefaultSchemaComparator comparator = new DefaultSchemaComparator();

    @Test
    void identicalSnapshotsPass() {
        TableMetadata table = sampleTable();
        SchemaSnapshot snap = new SchemaSnapshot("oracle", "HR", "v1", Instant.now(), List.of(table));
        DiffResult result = comparator.compare(snap, snap);
        assertFalse(result.hasErrors());
    }

    @Test
    void missingIndexReportsError() {
        TableMetadata expected = TableMetadata.builder("HR", "T")
                .column(col("ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("PK", List.of("ID")))
                .index(new IndexMetadata("IDX_DEPT", List.of("DEPT_ID"), false, "NORMAL", false))
                .build();
        TableMetadata actual = TableMetadata.builder("HR", "T")
                .column(col("ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("PK", List.of("ID")))
                .build();
        SchemaSnapshot exp = snap(expected);
        SchemaSnapshot act = snap(actual);
        DiffResult result = comparator.compare(exp, act);
        assertTrue(result.hasErrors());
        assertTrue(result.changes().stream().anyMatch(c -> "INDEX_MISSING".equals(c.code())));
    }

    private SchemaSnapshot snap(TableMetadata t) {
        return new SchemaSnapshot("x", "HR", "v1", Instant.now(), List.of(t));
    }

    private TableMetadata sampleTable() {
        return TableMetadata.builder("HR", "EMP")
                .comment("员工")
                .column(col("ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("PK_EMP", List.of("ID")))
                .build();
    }

    private ColumnMetadata col(String name, String mysqlType) {
        return ColumnMetadata.builder(name).mysqlType(mysqlType).oracleType("NUMBER").build();
    }
}
