package io.o2m.cli;

import io.o2m.core.json.JsonSupport;
import io.o2m.model.*;
import io.o2m.spi.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SchemaExportService {
    public static final String COMBINED_MYSQL_FILE = "schema.mysql.sql";
    public static final String COMBINED_ORACLE_FILE = "schema.oracle.sql";

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
        StringBuilder oracleAll = new StringBuilder();
        StringBuilder mysqlAll = new StringBuilder();
        for (TableMetadata table : snapshot.tables()) {
            GeneratedDdl o = oracleGen.generate(table);
            GeneratedDdl m = mysqlGen.generate(table);
            String baseName = table.name().toLowerCase();
            Files.writeString(schemaDir.resolve(baseName + ".oracle.sql"), o.oracleDdl() + "\n");
            Files.writeString(schemaDir.resolve(baseName + ".mysql.sql"), m.mysqlDdl() + "\n");
            appendDdl(oracleAll, o.oracleDdl());
            appendDdl(mysqlAll, m.mysqlDdl());

            TableCoverageReport report = coverage.validate(table);
            JsonSupport.mapper().writeValue(schemaDir.resolve(baseName + ".coverage.json").toFile(), report);
            if (ctx.appConfig().getCoverage().isFailOnBlocker() && report.hasBlockers()) {
                failures.add(table.name());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Coverage blockers for tables: " + failures);
        }
        if (!snapshot.tables().isEmpty()) {
            Files.writeString(schemaDir.resolve(COMBINED_MYSQL_FILE), mysqlAll.toString());
            Files.writeString(schemaDir.resolve(COMBINED_ORACLE_FILE), oracleAll.toString());
        }
        System.out.println("Exported " + snapshot.tables().size() + " tables to " + schemaDir
                + " (combined: " + COMBINED_MYSQL_FILE + ", " + COMBINED_ORACLE_FILE + ")");
    }

    private static void appendDdl(StringBuilder buf, String ddl) {
        if (ddl == null || ddl.isBlank()) {
            return;
        }
        if (!buf.isEmpty()) {
            buf.append("\n\n");
        }
        buf.append(ddl.trim()).append('\n');
    }
}
