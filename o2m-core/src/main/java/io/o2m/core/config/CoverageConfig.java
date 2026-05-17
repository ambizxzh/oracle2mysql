package io.o2m.core.config;

public class CoverageConfig {
    private boolean failOnBlocker = true;

    public boolean isFailOnBlocker() { return failOnBlocker; }
    public void setFailOnBlocker(boolean failOnBlocker) { this.failOnBlocker = failOnBlocker; }
}
