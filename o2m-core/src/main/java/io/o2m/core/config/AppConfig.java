package io.o2m.core.config;

public class AppConfig {
    private DatabaseConfig oracle = new DatabaseConfig();
    private DatabaseConfig mysql = new DatabaseConfig();
    private OutputConfig output = new OutputConfig();
    private RulesConfig rules = new RulesConfig();
    private MigrationConfig migration = new MigrationConfig();
    private CoverageConfig coverage = new CoverageConfig();
    private String typeMappingFile = "./mapping/type-mapping.yaml";

    public DatabaseConfig getOracle() { return oracle; }
    public void setOracle(DatabaseConfig oracle) { this.oracle = oracle; }
    public DatabaseConfig getMysql() { return mysql; }
    public void setMysql(DatabaseConfig mysql) { this.mysql = mysql; }
    public OutputConfig getOutput() { return output; }
    public void setOutput(OutputConfig output) { this.output = output; }
    public RulesConfig getRules() { return rules; }
    public void setRules(RulesConfig rules) { this.rules = rules; }
    public MigrationConfig getMigration() { return migration; }
    public void setMigration(MigrationConfig migration) { this.migration = migration; }
    public CoverageConfig getCoverage() { return coverage; }
    public void setCoverage(CoverageConfig coverage) { this.coverage = coverage; }
    public String getTypeMappingFile() { return typeMappingFile; }
    public void setTypeMappingFile(String typeMappingFile) { this.typeMappingFile = typeMappingFile; }
}
