package io.o2m.cli;

import io.o2m.core.json.JsonSupport;
import io.o2m.model.*;
import io.o2m.spi.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SchemaExportService {
    private final O2mContext ctx;

    public SchemaExportService(O2mContext ctx) {
        this.ctx = ctx;
    }

    public void export(String oracleSchema, String tag, Path outputDir, List<String> tables) throws Exception {
        SchemaSnapshot snapshot = ctx.pipeline().collectOracle(oracleSchema, tables, tag);
        writeSnapshot(snapshot, outputDir, tag);
    }

    public void writeSnapshot(SchemaSnapshot snapshot, Path outputDir, String tag) throws Exception {
        Path out = outputDir != null ? outputDir : Path.of(ctx.appConfig().getOutput().getBaseDir());
        Path schemaDir = out.resolve("schema");
        Files.createDirectories(schemaDir);

        if (tag != null) {
            ctx.registry().require(SnapshotStore.class).save(snapshot, out);
        }

        OracleDdlGenerator oracleGen = ctx.registry().require(OracleDdlGenerator.class);
        MysqlDdlGenerator mysqlGen = ctx.registry().require(MysqlDdlGenerator.class);
        CoverageValidator coverage = ctx.registry().require(CoverageValidator.class);

        List<String> failures = new ArrayList<>();
        for (TableMetadata table : snapshot.tables()) {
            GeneratedDdl o = oracleGen.generate(table);
            GeneratedDdl m = mysqlGen.generate(table);
            String baseName = table.name().toLowerCase();
            Files.writeString(schemaDir.resolve(baseName + ".oracle.sql"), o.oracleDdl() + "\n");
            Files.writeString(schemaDir.resolve(baseName + ".mysql.sql"), m.mysqlDdl() + "\n");

            TableCoverageReport report = coverage.validate(table);
            JsonSupport.mapper().writeValue(schemaDir.resolve(baseName + ".coverage.json").toFile(), report);
            if (ctx.appConfig().getCoverage().isFailOnBlocker() && report.hasBlockers()) {
                failures.add(table.name());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Coverage blockers for tables: " + failures);
        }
        System.out.println("Exported " + snapshot.tables().size() + " tables to " + schemaDir);
    }
}
