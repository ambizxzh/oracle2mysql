package io.o2m.translate.type;

import io.o2m.core.config.TypeMappingConfig;
import io.o2m.model.ColumnMetadata;
import io.o2m.model.PrimaryKeyMetadata;
import io.o2m.model.TableMetadata;
import io.o2m.model.TypeMappingReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberTypeMappingStrategyTest {
    private NumberTypeMappingStrategy strategy;

    @BeforeEach
    void setUp() {
        TypeMappingConfig config = new TypeMappingConfig();
        TypeMappingConfig.NumberSemanticConfig ns = new TypeMappingConfig.NumberSemanticConfig();
        ns.setIdColumnPatterns(java.util.List.of("(?i)^id$", "(?i)_id$"));
        ns.setPkPrefer("bigint");
        ns.setDefaultUnspecified("DECIMAL(38,16)");
        ns.setIntegerNumberPrefer("bigint");
        config.setNumberSemantic(ns);
        strategy = new NumberTypeMappingStrategy(config);
    }

    @Test
    void numberWithoutPrecisionOnPkMapsToBigint() {
        TableMetadata table = tableWithPk("EMPLOYEES", "EMPLOYEE_ID");
        ColumnMetadata col = ColumnMetadata.builder("EMPLOYEE_ID")
                .oracleType("NUMBER").nullable(false).build();
        ColumnMetadata mapped = strategy.mapColumn(table, col);
        assertEquals("BIGINT", mapped.mysqlType());
        assertEquals(TypeMappingReason.NUMBER_UNSPECIFIED_PK, mapped.typeMappingReason());
    }

    @Test
    void numberWithoutPrecisionOnIdColumnMapsToBigint() {
        TableMetadata table = TableMetadata.builder("HR", "ORDERS").build();
        ColumnMetadata col = ColumnMetadata.builder("CUSTOMER_ID").oracleType("NUMBER").build();
        ColumnMetadata mapped = strategy.mapColumn(table, col);
        assertEquals("BIGINT", mapped.mysqlType());
        assertEquals(TypeMappingReason.NUMBER_UNSPECIFIED_ID_PATTERN, mapped.typeMappingReason());
    }

    @Test
    void numberWithoutPrecisionDefaultDecimal() {
        TableMetadata table = TableMetadata.builder("HR", "ORDERS").build();
        ColumnMetadata col = ColumnMetadata.builder("AMOUNT").oracleType("NUMBER").build();
        ColumnMetadata mapped = strategy.mapColumn(table, col);
        assertEquals("DECIMAL(38,16)", mapped.mysqlType());
        assertEquals(TypeMappingReason.NUMBER_UNSPECIFIED_DEFAULT, mapped.typeMappingReason());
    }

    @Test
    void numberExplicitPs() {
        TableMetadata table = TableMetadata.builder("HR", "T").build();
        ColumnMetadata col = ColumnMetadata.builder("PRICE")
                .oracleType("NUMBER").dataPrecision(10).dataScale(2).build();
        ColumnMetadata mapped = strategy.mapColumn(table, col);
        assertEquals("DECIMAL(10,2)", mapped.mysqlType());
        assertEquals(TypeMappingReason.NUMBER_EXPLICIT_PS, mapped.typeMappingReason());
    }

    @Test
    void number38_0MapsToBigint() {
        TableMetadata table = TableMetadata.builder("HR", "T").build();
        ColumnMetadata col = ColumnMetadata.builder("X")
                .oracleType("NUMBER").dataPrecision(38).dataScale(0).build();
        ColumnMetadata mapped = strategy.mapColumn(table, col);
        assertEquals("BIGINT", mapped.mysqlType());
    }

    private TableMetadata tableWithPk(String table, String pkCol) {
        return TableMetadata.builder("HR", table)
                .primaryKey(new PrimaryKeyMetadata("PK_" + table, java.util.List.of(pkCol)))
                .build();
    }
}
