package io.o2m.cli;

import io.o2m.diff.SchemaMonitor;
import io.o2m.model.DiffResult;
import io.o2m.model.SchemaSnapshot;
import io.o2m.mysql.MysqlMetadataCollector;
import io.o2m.spi.SnapshotStore;

import java.nio.file.Path;
import java.util.List;

/**
 * Compare Oracle (live dictionary + type mapping) against MySQL (live metadata) in one step.
 */
public class LiveDiffService {
    private final O2mContext ctx;

    public LiveDiffService(O2mContext ctx) {
        this.ctx = ctx;
    }

    public DiffResult compare(String oracleSchema, String mysqlDatabase, List<String> tables, String tag,
                              boolean saveExpected) throws Exception {
        String versionTag = tag != null ? tag : "live-diff";
        SchemaSnapshot expected = ctx.pipeline().collectOracle(oracleSchema, tables, versionTag);

        if (saveExpected) {
            Path base = Path.of(ctx.appConfig().getOutput().getBaseDir());
            ctx.registry().require(SnapshotStore.class).save(expected, base);
        }

        List<String> tableFilter = tables;
        if (tableFilter == null || tableFilter.isEmpty()) {
            boolean upper = "upper".equalsIgnoreCase(ctx.appConfig().getRules().getIdentifierCase());
            tableFilter = expected.tables().stream()
                    .map(t -> upper ? t.name().toUpperCase() : t.name().toLowerCase())
                    .toList();
        }
        String db = mysqlDatabase != null ? mysqlDatabase : ctx.appConfig().getMysql().getDatabase();
        MysqlMetadataCollector mysqlCollector = ctx.registry().require(MysqlMetadataCollector.class);
        SchemaSnapshot actual = mysqlCollector.collect(db, tableFilter, versionTag);

        SchemaMonitor monitor = ctx.registry().require(SchemaMonitor.class);
        return monitor.compare(expected, actual);
    }
}
