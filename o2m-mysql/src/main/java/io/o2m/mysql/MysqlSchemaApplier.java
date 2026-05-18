package io.o2m.mysql;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Stream;

public class MysqlSchemaApplier {
    public static final String COMBINED_MYSQL_FILE = "schema.mysql.sql";

    private final DataSource dataSource;

    public MysqlSchemaApplier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void apply(Path input, boolean dryRun) throws Exception {
        if (Files.isRegularFile(input)) {
            applyFile(input, dryRun);
            return;
        }
        applyDirectory(input, dryRun);
    }

    public void applyDirectory(Path schemaDir, boolean dryRun) throws Exception {
        Path combined = schemaDir.resolve(COMBINED_MYSQL_FILE);
        if (Files.isRegularFile(combined)) {
            applyFile(combined, dryRun);
            return;
        }
        try (Stream<Path> files = Files.list(schemaDir).filter(this::isPerTableMysqlDdl)) {
            for (Path file : files.sorted().toList()) {
                applyFile(file, dryRun);
            }
        }
    }

    private boolean isPerTableMysqlDdl(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".mysql.sql") && !name.equals(COMBINED_MYSQL_FILE);
    }

    public void applyFile(Path file, boolean dryRun) throws Exception {
        String sql = Files.readString(file);
        if (dryRun) {
            System.out.println("-- DRY-RUN " + file.getFileName());
            System.out.println(sql);
        } else {
            executeScript(sql);
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
