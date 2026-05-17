package io.o2m.model;

import java.util.List;

public record IndexMetadata(
        String name,
        List<String> columns,
        boolean unique,
        String indexType,
        boolean gap
) {}
