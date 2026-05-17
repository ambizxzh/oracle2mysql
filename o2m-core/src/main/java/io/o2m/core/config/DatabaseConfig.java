package io.o2m.core.config;

public class DatabaseConfig {
    private String jdbcUrl;
    private String username;
    private String password;
    private String schema;
    private String database;

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
}
