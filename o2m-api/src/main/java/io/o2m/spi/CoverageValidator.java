package io.o2m.spi;

import io.o2m.model.TableCoverageReport;
import io.o2m.model.TableMetadata;

public interface CoverageValidator {
    TableCoverageReport validate(TableMetadata table);
}
