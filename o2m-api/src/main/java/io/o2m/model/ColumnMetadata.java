package io.o2m.model;

import java.util.Optional;

public record ColumnMetadata(
        String name,
        String oracleType,
        Integer dataLength,
        Integer charLength,
        Integer dataPrecision,
        Integer dataScale,
        boolean nullable,
        String defaultValue,
        String comment,
        String mysqlType,
        TypeMappingReason typeMappingReason,
        boolean manualReview
) {
    public ColumnMetadata {
        name = name != null ? name : "";
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public ColumnMetadata withMysqlType(String mysqlType, TypeMappingReason reason) {
        return new ColumnMetadata(name, oracleType, dataLength, charLength, dataPrecision, dataScale,
                nullable, defaultValue, comment, mysqlType, reason, manualReview);
    }

    public static final class Builder {
        private final String name;
        private String oracleType;
        private Integer dataLength;
        private Integer charLength;
        private Integer dataPrecision;
        private Integer dataScale;
        private boolean nullable = true;
        private String defaultValue;
        private String comment;
        private String mysqlType;
        private TypeMappingReason typeMappingReason;
        private boolean manualReview;

        private Builder(String name) {
            this.name = name;
        }

        public Builder oracleType(String v) { this.oracleType = v; return this; }
        public Builder dataLength(Integer v) { this.dataLength = v; return this; }
        public Builder charLength(Integer v) { this.charLength = v; return this; }
        public Builder dataPrecision(Integer v) { this.dataPrecision = v; return this; }
        public Builder dataScale(Integer v) { this.dataScale = v; return this; }
        public Builder nullable(boolean v) { this.nullable = v; return this; }
        public Builder defaultValue(String v) { this.defaultValue = v; return this; }
        public Builder comment(String v) { this.comment = v; return this; }
        public Builder mysqlType(String v) { this.mysqlType = v; return this; }
        public Builder typeMappingReason(TypeMappingReason v) { this.typeMappingReason = v; return this; }
        public Builder manualReview(boolean v) { this.manualReview = v; return this; }

        public ColumnMetadata build() {
            return new ColumnMetadata(name, oracleType, dataLength, charLength, dataPrecision, dataScale,
                    nullable, defaultValue, comment, mysqlType, typeMappingReason, manualReview);
        }
    }

    public Optional<String> commentOptional() {
        return Optional.ofNullable(comment).filter(s -> !s.isBlank());
    }
}
