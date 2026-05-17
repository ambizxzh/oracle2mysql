package io.o2m.mysql;

import io.o2m.core.config.MigrationConfig;
import io.o2m.spi.MigrationExecutor;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

public class MysqlMigrationExecutor implements MigrationExecutor {
    private final DataSource dataSource;
    private final MigrationConfig config;

    public MysqlMigrationExecutor(DataSource dataSource, MigrationConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }

    @Override
    public void ensureHistoryTable() throws Exception {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                  version VARCHAR(64) NOT NULL PRIMARY KEY,
                  description VARCHAR(256),
                  script_name VARCHAR(256) NOT NULL,
                  checksum VARCHAR(64) NOT NULL,
                  installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  success TINYINT(1) NOT NULL
                ) ENGINE=InnoDB
                """.formatted(config.getHistoryTable());
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Override
    public void applyScripts(List<Path> scripts, boolean dryRun) throws Exception {
        ensureHistoryTable();
        for (Path script : scripts) {
            String version = script.getFileName().toString().replace(".sql", "");
            if (isApplied(version)) {
                continue;
            }
            String content = Files.readString(script);
            if (dryRun) {
                System.out.println("-- DRY-RUN: " + script);
                System.out.println(content);
                continue;
            }
            try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
                for (String stmt : splitStatements(content)) {
                    if (!stmt.isBlank()) st.execute(stmt);
                }
            }
            recordHistory(version, script.getFileName().toString(), checksum(content), true);
        }
    }

    @Override
    public boolean isApplied(String version) throws Exception {
        String sql = "SELECT 1 FROM " + config.getHistoryTable() + " WHERE version = ? AND success = 1";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordHistory(String version, String scriptName, String checksum, boolean success) throws Exception {
        String sql = "INSERT INTO " + config.getHistoryTable()
                + " (version, description, script_name, checksum, success) VALUES (?,?,?,?,?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setString(2, version);
            ps.setString(3, scriptName);
            ps.setString(4, checksum);
            ps.setInt(5, success ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private String checksum(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(content.getBytes()));
    }

    private List<String> splitStatements(String content) {
        return List.of(content.split(";")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s + ";")
                .toList();
    }
}
