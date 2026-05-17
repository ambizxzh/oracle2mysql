package io.o2m.translate.type;

import io.o2m.core.config.TypeMappingConfig;
import io.o2m.model.ColumnMetadata;
import io.o2m.model.TableMetadata;
import io.o2m.model.TypeMappingReason;
import io.o2m.spi.TypeMappingStrategy;

import java.util.List;
import java.util.regex.Pattern;

public class NumberTypeMappingStrategy implements TypeMappingStrategy {
    private final TypeMappingConfig config;
    private final List<Pattern> idPatterns;

    public NumberTypeMappingStrategy(TypeMappingConfig config) {
        this.config = config;
        this.idPatterns = config.getNumberSemantic().getIdColumnPatterns().stream()
                .map(Pattern::compile)
                .toList();
    }

    @Override
    public ColumnMetadata mapColumn(TableMetadata table, ColumnMetadata column) {
        TypeMappingConfig.TypeOverride override = findOverride(table, column);
        if (override != null) {
            return column.withMysqlType(override.getMysqlType(), TypeMappingReason.YAML_OVERRIDE);
        }

        Integer p = column.dataPrecision();
        Integer s = column.dataScale();

        if (p == null && s == null) {
            if (table.isPrimaryKeyColumn(column.name())) {
                return column.withMysqlType(pkPrefer(), TypeMappingReason.NUMBER_UNSPECIFIED_PK);
            }
            if (matchesIdPattern(column.name())) {
                return column.withMysqlType("BIGINT", TypeMappingReason.NUMBER_UNSPECIFIED_ID_PATTERN);
            }
            return column.withMysqlType(config.getNumberSemantic().getDefaultUnspecified(),
                    TypeMappingReason.NUMBER_UNSPECIFIED_DEFAULT);
        }

        int scale = s != null ? s : 0;
        int precision = p != null ? p : 38;

        if (scale == 0) {
            if (precision == 38 && "bigint".equalsIgnoreCase(config.getNumberSemantic().getIntegerNumberPrefer())) {
                return column.withMysqlType("BIGINT", TypeMappingReason.NUMBER_EXPLICIT_INTEGER);
            }
            String intType = integerPrefer(precision);
            return column.withMysqlType(intType, TypeMappingReason.NUMBER_EXPLICIT_INTEGER);
        }

        int maxP = config.getNumberSemantic().getMysqlDecimalMaxPrecision();
        int maxS = config.getNumberSemantic().getMysqlDecimalMaxScale();
        int cp = Math.min(precision, maxP);
        int cs = Math.min(scale, maxS);
        TypeMappingReason reason = (cp != precision || cs != scale)
                ? TypeMappingReason.NUMBER_CLAMPED : TypeMappingReason.NUMBER_EXPLICIT_PS;
        return column.withMysqlType("DECIMAL(" + cp + "," + cs + ")", reason);
    }

    private String pkPrefer() {
        return "decimal38_0".equalsIgnoreCase(config.getNumberSemantic().getPkPrefer())
                ? "DECIMAL(38,0)" : "BIGINT";
    }

    private String integerPrefer(int precision) {
        if ("bigint".equalsIgnoreCase(config.getNumberSemantic().getIntegerNumberPrefer())) {
            if (precision <= 19) {
                return "BIGINT";
            }
        }
        if (precision <= 10) {
            return "INT";
        }
        return "DECIMAL(" + Math.min(precision, config.getNumberSemantic().getMysqlDecimalMaxPrecision()) + ",0)";
    }

    private boolean matchesIdPattern(String name) {
        for (Pattern p : idPatterns) {
            if (p.matcher(name).find()) return true;
        }
        return false;
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
