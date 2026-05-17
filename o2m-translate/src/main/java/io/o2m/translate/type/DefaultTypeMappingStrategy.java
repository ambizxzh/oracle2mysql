package io.o2m.translate.type;

import io.o2m.core.config.TypeMappingConfig;
import io.o2m.model.ColumnMetadata;
import io.o2m.model.TableMetadata;
import io.o2m.model.TypeMappingReason;
import io.o2m.spi.TypeMappingStrategy;

public class DefaultTypeMappingStrategy implements TypeMappingStrategy {
    private final TypeMappingConfig config;

    public DefaultTypeMappingStrategy(TypeMappingConfig config) {
        this.config = config;
    }

    @Override
    public ColumnMetadata mapColumn(TableMetadata table, ColumnMetadata column) {
        TypeMappingConfig.TypeOverride override = findOverride(table, column);
        if (override != null) {
            return column.withMysqlType(override.getMysqlType(), TypeMappingReason.YAML_OVERRIDE);
        }

        String oracleType = column.oracleType();
        if (oracleType == null) {
            return column.withMysqlType("LONGTEXT", TypeMappingReason.MANUAL_REVIEW);
        }

        String upper = oracleType.toUpperCase();
        if (upper.startsWith("TIMESTAMP")) {
            int scale = column.dataScale() != null ? column.dataScale() : 6;
            return column.withMysqlType("DATETIME(" + scale + ")", TypeMappingReason.DEFAULT_TYPE_MAP);
        }

        String template = config.getDefaults().get(upper);
        if (template == null) {
            for (var e : config.getDefaults().entrySet()) {
                if (upper.startsWith(e.getKey())) {
                    template = e.getValue();
                    break;
                }
            }
        }
        if (template == null) {
            return column.withMysqlType(upper, TypeMappingReason.MANUAL_REVIEW);
        }

        int length = column.charLength() != null ? column.charLength()
                : (column.dataLength() != null ? column.dataLength() : 255);
        String mysql = template.replace("{length}", String.valueOf(length))
                .replace("{precision}", String.valueOf(column.dataPrecision() != null ? column.dataPrecision() : 38))
                .replace("{scale}", String.valueOf(column.dataScale() != null ? column.dataScale() : 0));

        String def = column.defaultValue();
        boolean manual = false;
        if (def != null && !def.isBlank()) {
            String mapped = config.getDefaultsValue().entrySet().stream()
                    .filter(e -> def.toUpperCase().contains(e.getKey()))
                    .map(e -> e.getValue())
                    .findFirst()
                    .orElse(null);
            if (mapped == null) manual = true;
        }

        ColumnMetadata result = column.withMysqlType(mysql, TypeMappingReason.DEFAULT_TYPE_MAP);
        if (manual) {
            return new ColumnMetadata(result.name(), result.oracleType(), result.dataLength(), result.charLength(),
                    result.dataPrecision(), result.dataScale(), result.nullable(), result.defaultValue(),
                    result.comment(), result.mysqlType(), TypeMappingReason.MANUAL_REVIEW, true);
        }
        return result;
    }

    private TypeMappingConfig.TypeOverride findOverride(TableMetadata table, ColumnMetadata column) {
        for (TypeMappingConfig.TypeOverride o : config.getOverrides()) {
            if (o.getColumn() != null && o.getColumn().equalsIgnoreCase(column.name())) {
                if (o.getTable() == null || o.getTable().equalsIgnoreCase(table.name())) {
                    if (o.getSchema() == null || o.getSchema().equalsIgnoreCase(table.schema())) {
                        return o;
                    }
                }
            }
        }
        return null;
    }
}
