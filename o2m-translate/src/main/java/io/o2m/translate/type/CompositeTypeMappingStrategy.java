package io.o2m.translate.type;

import io.o2m.core.config.TypeMappingConfig;
import io.o2m.model.ColumnMetadata;
import io.o2m.model.TableMetadata;
import io.o2m.spi.TypeMappingStrategy;

public class CompositeTypeMappingStrategy implements TypeMappingStrategy {
    private final NumberTypeMappingStrategy numberStrategy;
    private final DefaultTypeMappingStrategy defaultStrategy;

    public CompositeTypeMappingStrategy(TypeMappingConfig config) {
        this.numberStrategy = new NumberTypeMappingStrategy(config);
        this.defaultStrategy = new DefaultTypeMappingStrategy(config);
    }

    @Override
    public ColumnMetadata mapColumn(TableMetadata table, ColumnMetadata column) {
        if ("NUMBER".equalsIgnoreCase(column.oracleType())) {
            return numberStrategy.mapColumn(table, column);
        }
        return defaultStrategy.mapColumn(table, column);
    }
}
