package io.o2m.mysql;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Stream;

public class MysqlSchemaApplier {
    private final DataSource dataSource;

    public MysqlSchemaApplier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void applyDirectory(Path schemaDir, boolean dryRun) throws Exception {
        try (Stream<Path> files = Files.list(schemaDir).filter(p -> p.toString().endsWith(".mysql.sql"))) {
            for (Path file : files.sorted().toList()) {
                String sql = Files.readString(file);
                if (dryRun) {
                    System.out.println("-- DRY-RUN " + file.getFileName());
                    System.out.println(sql);
                } else {
                    executeScript(sql);
                }
            }
        }
    }

    public void executeScript(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isBlank()) {
                    st.execute(trimmed);
                }
            }
        }
    }
}
