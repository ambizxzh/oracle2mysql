package io.o2m.core.config;

public class RulesConfig {
    private String identifierCase = "lower";
    private boolean quoteIdentifiers = true;
    private String charset = "utf8mb4";
    private String collation = "utf8mb4_unicode_ci";
    private String engine = "InnoDB";

    public String getIdentifierCase() { return identifierCase; }
    public void setIdentifierCase(String identifierCase) { this.identifierCase = identifierCase; }
    public boolean isQuoteIdentifiers() { return quoteIdentifiers; }
    public void setQuoteIdentifiers(boolean quoteIdentifiers) { this.quoteIdentifiers = quoteIdentifiers; }
    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }
    public String getCollation() { return collation; }
    public void setCollation(String collation) { this.collation = collation; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
}
