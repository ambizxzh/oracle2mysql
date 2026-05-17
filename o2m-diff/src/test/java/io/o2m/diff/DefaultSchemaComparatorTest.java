package io.o2m.diff;

import io.o2m.core.config.RulesConfig;
import io.o2m.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSchemaComparatorTest {
    private final RulesConfig rules = defaultRules();
    private final DefaultSchemaComparator comparator = new DefaultSchemaComparator(rules);

    private static RulesConfig defaultRules() {
        RulesConfig r = new RulesConfig();
        r.setIdentifierCase("lower");
        r.setQuoteIdentifiers(true);
        return r;
    }

    @Test
    void identicalSnapshotsPass() {
        TableMetadata table = sampleTable();
        SchemaSnapshot snap = new SchemaSnapshot("oracle", "HR", "v1", Instant.now(), List.of(table));
        DiffResult result = comparator.compare(snap, snap);
        assertFalse(result.hasErrors());
    }

    @Test
    void pkNameMismatchReportsError() {
        TableMetadata expected = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("DEPARTMENT_ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("DEPT_PK", List.of("DEPARTMENT_ID")))
                .build();
        TableMetadata actual = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("department_id", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("wrong_pk", List.of("department_id")))
                .build();
        DiffResult result = comparator.compare(snap(expected), snap(actual));
        assertTrue(result.hasErrors());
        assertTrue(result.changes().stream().anyMatch(c -> "PK_NAME_DIFF".equals(c.code())));
    }

    @Test
    void pkNameMatchWhenMysqlUsesPrimaryAlias() {
        TableMetadata expected = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("DEPARTMENT_ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("DEPT_PK", List.of("DEPARTMENT_ID")))
                .build();
        TableMetadata actual = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("department_id", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("PRIMARY", List.of("department_id")))
                .build();
        DiffResult result = comparator.compare(snap(expected), snap(actual));
        assertFalse(result.changes().stream().anyMatch(c -> "PK_NAME_DIFF".equals(c.code())));
    }

    @Test
    void pkNameMatchWhenMysqlUsesMappedOracleName() {
        TableMetadata expected = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("DEPARTMENT_ID", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("DEPT_PK", List.of("DEPARTMENT_ID")))
                .build();
        TableMetadata actual = TableMetadata.builder("HR", "DEPARTMENTS")
                .column(col("department_id", "BIGINT"))
                .primaryKey(new PrimaryKeyMetadata("dept_pk", List.of("department_id")))
                .build();
        DiffResult result = comparator.compare(snap(expected), snap(actual));
        assertFalse(result.changes().stream().anyMatch(c -> "PK_NAME_DIFF".equals(c.code())));
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
