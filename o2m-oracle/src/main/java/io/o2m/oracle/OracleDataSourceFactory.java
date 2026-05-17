package io.o2m.oracle;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.o2m.core.config.DatabaseConfig;

import javax.sql.DataSource;

public final class OracleDataSourceFactory {
    private OracleDataSourceFactory() {}

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getJdbcUrl());
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        hc.setMaximumPoolSize(5);
        hc.setPoolName("o2m-oracle");
        return new HikariDataSource(hc);
    }
}
