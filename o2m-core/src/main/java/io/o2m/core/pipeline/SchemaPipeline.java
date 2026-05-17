package io.o2m.core.pipeline;

import io.o2m.core.config.AppConfig;
import io.o2m.core.config.TypeMappingConfig;
import io.o2m.core.registry.ServiceRegistry;
import io.o2m.model.SchemaSnapshot;
import io.o2m.model.TableMetadata;
import io.o2m.spi.MetadataCollector;
import io.o2m.spi.TypeMappingStrategy;

import java.util.ArrayList;
import java.util.List;

public class SchemaPipeline {
    private final ServiceRegistry registry;
    private final AppConfig appConfig;
    private final TypeMappingConfig typeMappingConfig;

    public SchemaPipeline(ServiceRegistry registry, AppConfig appConfig, TypeMappingConfig typeMappingConfig) {
        this.registry = registry;
        this.appConfig = appConfig;
        this.typeMappingConfig = typeMappingConfig;
    }

    public SchemaSnapshot collectOracle(String schema, List<String> tables, String tag) throws Exception {
        MetadataCollector collector = registry.require(MetadataCollector.class);
        SchemaSnapshot raw = collector.collect(schema, tables, tag);
        return applyTypeMapping(raw);
    }

    public SchemaSnapshot applyTypeMapping(SchemaSnapshot snapshot) {
        TypeMappingStrategy mapper = registry.require(TypeMappingStrategy.class);
        List<TableMetadata> mapped = new ArrayList<>();
        for (TableMetadata table : snapshot.tables()) {
            List<io.o2m.model.ColumnMetadata> cols = new ArrayList<>();
            for (io.o2m.model.ColumnMetadata col : table.columns()) {
                cols.add(mapper.mapColumn(table, col));
            }
            mapped.add(new TableMetadata(
                    table.schema(), table.name(), table.comment(), cols,
                    table.primaryKey(), table.uniqueKeys(), table.foreignKeys(),
                    table.checks(), table.indexes()));
        }
        return new SchemaSnapshot(snapshot.source(), snapshot.schemaName(), snapshot.versionTag(),
                snapshot.capturedAt(), mapped);
    }

    public ServiceRegistry registry() { return registry; }
    public AppConfig appConfig() { return appConfig; }
    public TypeMappingConfig typeMappingConfig() { return typeMappingConfig; }
}
