package io.o2m.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.o2m.core.config.DatabaseConfig;

import javax.sql.DataSource;

public final class MysqlDataSourceFactory {
    private MysqlDataSourceFactory() {}

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getJdbcUrl());
        hc.setUsername(config.getUsername());
        hc.setPassword(config.getPassword());
        hc.setMaximumPoolSize(5);
        hc.setPoolName("o2m-mysql");
        return new HikariDataSource(hc);
    }
}
