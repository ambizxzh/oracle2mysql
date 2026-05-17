package io.o2m.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeMappingConfig {
    private Map<String, String> defaults = new HashMap<>();
    private NumberSemanticConfig numberSemantic = new NumberSemanticConfig();
    private Map<String, String> defaultsValue = new HashMap<>();
    private List<TypeOverride> overrides = new ArrayList<>();

    public Map<String, String> getDefaults() { return defaults; }
    public void setDefaults(Map<String, String> defaults) { this.defaults = defaults; }
    public NumberSemanticConfig getNumberSemantic() { return numberSemantic; }
    public void setNumberSemantic(NumberSemanticConfig numberSemantic) { this.numberSemantic = numberSemantic; }
    public Map<String, String> getDefaultsValue() { return defaultsValue; }
    public void setDefaultsValue(Map<String, String> defaultsValue) { this.defaultsValue = defaultsValue; }
    public List<TypeOverride> getOverrides() { return overrides; }
    public void setOverrides(List<TypeOverride> overrides) { this.overrides = overrides; }

    public static class NumberSemanticConfig {
        private List<String> idColumnPatterns = List.of("(?i)^id$", "(?i)_id$");
        private String pkPrefer = "bigint";
        private String defaultUnspecified = "DECIMAL(38,16)";
        private String integerNumberPrefer = "bigint";
        private int mysqlDecimalMaxPrecision = 65;
        private int mysqlDecimalMaxScale = 30;

        public List<String> getIdColumnPatterns() { return idColumnPatterns; }
        public void setIdColumnPatterns(List<String> idColumnPatterns) { this.idColumnPatterns = idColumnPatterns; }
        public String getPkPrefer() { return pkPrefer; }
        public void setPkPrefer(String pkPrefer) { this.pkPrefer = pkPrefer; }
        public String getDefaultUnspecified() { return defaultUnspecified; }
        public void setDefaultUnspecified(String defaultUnspecified) { this.defaultUnspecified = defaultUnspecified; }
        public String getIntegerNumberPrefer() { return integerNumberPrefer; }
        public void setIntegerNumberPrefer(String integerNumberPrefer) { this.integerNumberPrefer = integerNumberPrefer; }
        public int getMysqlDecimalMaxPrecision() { return mysqlDecimalMaxPrecision; }
        public void setMysqlDecimalMaxPrecision(int v) { this.mysqlDecimalMaxPrecision = v; }
        public int getMysqlDecimalMaxScale() { return mysqlDecimalMaxScale; }
        public void setMysqlDecimalMaxScale(int v) { this.mysqlDecimalMaxScale = v; }
    }

    public static class TypeOverride {
        private String schema;
        private String table;
        private String column;
        private String mysqlType;

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }
        public String getMysqlType() { return mysqlType; }
        public void setMysqlType(String mysqlType) { this.mysqlType = mysqlType; }
    }
}
