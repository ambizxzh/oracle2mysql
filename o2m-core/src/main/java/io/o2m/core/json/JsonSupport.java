package io.o2m.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.o2m.model.SchemaSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonSupport() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static void writeSnapshot(SchemaSnapshot snapshot, Path file) throws Exception {
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), snapshot);
    }

    public static SchemaSnapshot readSnapshot(Path file) throws Exception {
        return MAPPER.readValue(file.toFile(), SchemaSnapshot.class);
    }
}
