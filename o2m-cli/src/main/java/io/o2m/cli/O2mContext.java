package io.o2m.cli;

import io.o2m.core.config.AppConfig;
import io.o2m.core.config.ConfigLoader;
import io.o2m.core.config.TypeMappingConfig;
import io.o2m.core.pipeline.SchemaPipeline;
import io.o2m.core.registry.ServiceRegistry;
import io.o2m.diff.DefaultSchemaComparator;
import io.o2m.diff.SchemaMonitor;
import io.o2m.migrate.DefaultMigrationPlanner;
import io.o2m.migrate.FileSnapshotStore;
import io.o2m.mysql.MysqlDataSourceFactory;
import io.o2m.mysql.MysqlMetadataCollector;
import io.o2m.mysql.MysqlMigrationExecutor;
import io.o2m.mysql.MysqlSchemaApplier;
import io.o2m.oracle.OracleDataSourceFactory;
import io.o2m.oracle.OracleMetadataCollector;
import io.o2m.spi.*;
import io.o2m.translate.coverage.TableCoverageChecker;
import io.o2m.translate.ddl.DefaultMysqlDdlGenerator;
import io.o2m.translate.ddl.DefaultOracleDdlGenerator;
import io.o2m.translate.parser.JsqlOracleDdlParser;
import io.o2m.translate.type.CompositeTypeMappingStrategy;

import javax.sql.DataSource;
import java.nio.file.Path;

public class O2mContext {
    private final AppConfig appConfig;
    private final TypeMappingConfig typeMappingConfig;
    private final ServiceRegistry registry;
    private final SchemaPipeline pipeline;

    public O2mContext(Path configPath) throws Exception {
        this.appConfig = ConfigLoader.loadApp(configPath);
        ConfigLoader.resolvePassword(appConfig.getOracle());
        ConfigLoader.resolvePassword(appConfig.getMysql());
        this.typeMappingConfig = ConfigLoader.loadTypeMapping(Path.of(appConfig.getTypeMappingFile()));
        this.registry = new ServiceRegistry();
        bootstrap();
        this.pipeline = new SchemaPipeline(registry, appConfig, typeMappingConfig);
    }

    private void bootstrap() {
        DataSource oracleDs = OracleDataSourceFactory.create(appConfig.getOracle());
        DataSource mysqlDs = MysqlDataSourceFactory.create(appConfig.getMysql());
        String mysqlDb = appConfig.getMysql().getDatabase() != null
                ? appConfig.getMysql().getDatabase()
                : appConfig.getMysql().getSchema();

        registry.register(MetadataCollector.class, new OracleMetadataCollector(oracleDs));
        registry.register(TypeMappingStrategy.class, new CompositeTypeMappingStrategy(typeMappingConfig));
        registry.register(OracleDdlGenerator.class, new DefaultOracleDdlGenerator());
        registry.register(MysqlDdlGenerator.class, new DefaultMysqlDdlGenerator(appConfig.getRules()));
        registry.register(CoverageValidator.class, new TableCoverageChecker());
        registry.register(SchemaComparator.class, new DefaultSchemaComparator(appConfig.getRules()));
        registry.register(DdlParser.class, new JsqlOracleDdlParser());
        registry.register(SnapshotStore.class, new FileSnapshotStore());
        registry.register(MigrationPlanner.class,
                new DefaultMigrationPlanner(registry.require(SchemaComparator.class), appConfig.getMigration()));
        registry.register(MigrationExecutor.class, new MysqlMigrationExecutor(mysqlDs, appConfig.getMigration()));

        registry.register(DataSource.class, mysqlDs);
        registry.register(MysqlMetadataCollector.class, new MysqlMetadataCollector(mysqlDs, mysqlDb));
        registry.register(MysqlSchemaApplier.class, new MysqlSchemaApplier(mysqlDs));
        registry.register(SchemaMonitor.class, new SchemaMonitor(registry.require(SchemaComparator.class)));
    }

    public AppConfig appConfig() { return appConfig; }
    public SchemaPipeline pipeline() { return pipeline; }
    public ServiceRegistry registry() { return registry; }
}
