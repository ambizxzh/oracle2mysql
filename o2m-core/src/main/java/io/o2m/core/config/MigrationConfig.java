package io.o2m.core.config;

public class MigrationConfig {
    private boolean allowDestructive = false;
    private String historyTable = "o2m_schema_history";

    public boolean isAllowDestructive() { return allowDestructive; }
    public void setAllowDestructive(boolean allowDestructive) { this.allowDestructive = allowDestructive; }
    public String getHistoryTable() { return historyTable; }
    public void setHistoryTable(String historyTable) { this.historyTable = historyTable; }
}
