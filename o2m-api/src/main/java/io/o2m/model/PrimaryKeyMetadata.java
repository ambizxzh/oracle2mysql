package io.o2m.model;

import java.util.List;

public record PrimaryKeyMetadata(String name, List<String> columns) {}
