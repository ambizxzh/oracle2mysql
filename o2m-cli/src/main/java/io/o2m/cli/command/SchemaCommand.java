package io.o2m.cli.command;

import io.o2m.cli.O2mContext;
import io.o2m.cli.SchemaExportService;
import io.o2m.core.json.JsonSupport;
import io.o2m.diff.SchemaMonitor;
import io.o2m.model.DiffResult;
import io.o2m.model.MigrationPlan;
import io.o2m.model.SchemaSnapshot;
import io.o2m.mysql.MysqlMetadataCollector;
import io.o2m.mysql.MysqlSchemaApplier;
import io.o2m.spi.*;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "schema", subcommands = {
        SchemaCommand.Export.class,
        SchemaCommand.ImportCmd.class,
        SchemaCommand.Diff.class,
        SchemaCommand.Apply.class,
        SchemaCommand.Verify.class,
        SchemaCommand.MigrateGroup.class
})
public class SchemaCommand implements Callable<Integer> {
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return 0;
    }

    static O2mContext ctx(Path config) throws Exception {
        Path cfg = config != null ? config : Path.of("config/application.yaml");
        if (!Files.exists(cfg)) {
            cfg = Path.of("config/application.yaml.example");
        }
        return new O2mContext(cfg);
    }

    @CommandLine.Command(name = "export", description = "Export Oracle schema to dual DDL + snapshot")
    static class Export implements Callable<Integer> {
        @CommandLine.Option(names = "--config", description = "Config file path")
        Path config;
        @CommandLine.Option(names = "--oracle-schema", required = true)
        String oracleSchema;
        @CommandLine.Option(names = "--tag", defaultValue = "v1")
        String tag;
        @CommandLine.Option(names = "--output")
        Path output;
        @CommandLine.Option(names = "--tables", split = ",")
        List<String> tables;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            new SchemaExportService(ctx).export(oracleSchema, tag, output, tables);
            return 0;
        }
    }

    @CommandLine.Command(name = "import", description = "Import Oracle DDL files into Canonical")
    static class ImportCmd implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--ddl", required = true)
        Path ddl;
        @CommandLine.Option(names = "--oracle-schema", required = true)
        String schema;
        @CommandLine.Option(names = "--tag", defaultValue = "import")
        String tag;
        @CommandLine.Option(names = "--output")
        Path output;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            SchemaSnapshot raw = ctx.registry().require(DdlParser.class).parse(ddl, schema, tag);
            SchemaSnapshot mapped = ctx.pipeline().applyTypeMapping(raw);
            Path out = output != null ? output : Path.of(ctx.appConfig().getOutput().getBaseDir());
            ctx.registry().require(SnapshotStore.class).save(mapped, out);
            new SchemaExportService(ctx).writeSnapshot(mapped, out, tag);
            return 0;
        }
    }

    @CommandLine.Command(name = "diff", description = "Compare expected snapshot vs MySQL")
    static class Diff implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--expected", required = true)
        Path expected;
        @CommandLine.Option(names = "--mysql-database")
        String mysqlDb;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            SchemaSnapshot exp = JsonSupport.readSnapshot(expected.resolve("canonical.json"));
            String db = mysqlDb != null ? mysqlDb : ctx.appConfig().getMysql().getDatabase();
            MysqlMetadataCollector mc = ctx.registry().require(MysqlMetadataCollector.class);
            SchemaSnapshot act = mc.collect(db, null, "actual");
            SchemaMonitor monitor = ctx.registry().require(SchemaMonitor.class);
            DiffResult result = monitor.compare(exp, act);
            Path reports = Path.of(ctx.appConfig().getOutput().getBaseDir()).resolve("reports");
            monitor.writeReports(result, reports);
            System.out.println("Diff: " + result.changes().size() + " changes, errors=" + result.hasErrors());
            return result.hasErrors() ? 1 : 0;
        }
    }

    @CommandLine.Command(name = "apply", description = "Apply generated MySQL DDL")
    static class Apply implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--input", required = true)
        Path input;
        @CommandLine.Option(names = "--dry-run")
        boolean dryRun;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            ctx.registry().require(MysqlSchemaApplier.class).applyDirectory(input, dryRun);
            return 0;
        }
    }

    @CommandLine.Command(name = "verify", description = "Verify MySQL matches expected snapshot after apply")
    static class Verify implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--expected", required = true)
        Path expected;
        @CommandLine.Option(names = "--mysql-database")
        String mysqlDb;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            Path canonical = expected.toString().endsWith("canonical.json")
                    ? expected : expected.resolve("canonical.json");
            SchemaSnapshot exp = JsonSupport.readSnapshot(canonical);
            String db = mysqlDb != null ? mysqlDb : ctx.appConfig().getMysql().getDatabase();
            MysqlMetadataCollector mc = ctx.registry().require(MysqlMetadataCollector.class);
            SchemaSnapshot act = mc.collect(db, null, "verify");
            SchemaMonitor monitor = ctx.registry().require(SchemaMonitor.class);
            DiffResult result = monitor.compare(exp, act);
            Path reports = Path.of(ctx.appConfig().getOutput().getBaseDir()).resolve("reports");
            monitor.writeVerifyReport(result, reports, result.passed());
            System.out.println("Verify: " + (result.passed() ? "PASS" : "FAIL"));
            return result.passed() ? 0 : 1;
        }
    }

    @CommandLine.Command(name = "migrate", subcommands = {MigratePlan.class, MigrateApplyCmd.class},
            description = "Schema evolution")
    static class MigrateGroup implements Callable<Integer> {
        @Override
        public Integer call() {
            return 0;
        }
    }

    @CommandLine.Command(name = "plan", description = "Plan migration from snapshot v1 to v2")
    static class MigratePlan implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--from", required = true)
        String from;
        @CommandLine.Option(names = "--tag", required = true)
        String to;
        @CommandLine.Option(names = "--output")
        Path output;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            Path base = output != null ? output : Path.of(ctx.appConfig().getOutput().getBaseDir());
            SnapshotStore store = ctx.registry().require(SnapshotStore.class);
            SchemaSnapshot fromSnap = store.load(base, from).orElseThrow();
            SchemaSnapshot toSnap = ctx.pipeline().collectOracle(
                    ctx.appConfig().getOracle().getSchema(), null, to);
            MigrationPlan plan = ctx.registry().require(MigrationPlanner.class).plan(fromSnap, toSnap);
            Path migDir = base.resolve("migrations");
            Files.createDirectories(migDir);
            StringBuilder sql = new StringBuilder();
            int seq = 1;
            for (var ch : plan.changes()) {
                sql.append(ch.sql()).append("\n");
            }
            String fileName = String.format("V%03d__%s_to_%s.sql", seq, from, to);
            Files.writeString(migDir.resolve(fileName), sql.toString());
            store.save(toSnap, base);
            System.out.println("Migration plan written: " + migDir.resolve(fileName));
            return 0;
        }
    }

    @CommandLine.Command(name = "apply", description = "Apply migration scripts")
    static class MigrateApplyCmd implements Callable<Integer> {
        @CommandLine.Option(names = "--config")
        Path config;
        @CommandLine.Option(names = "--input", required = true)
        Path input;
        @CommandLine.Option(names = "--dry-run")
        boolean dryRun;

        @Override
        public Integer call() throws Exception {
            O2mContext ctx = ctx(config);
            List<Path> scripts = Files.list(input).filter(p -> p.toString().endsWith(".sql")).sorted().toList();
            ctx.registry().require(MigrationExecutor.class).applyScripts(scripts, dryRun);
            return 0;
        }
    }
}
