package com.configs;

public enum ConfigType {
    STATUS("process-flow-status.json"),
    PROCESS("process-flow.json");

    private final String defaultClasspathFile;

    ConfigType(String defaultClasspathFile) {
        this.defaultClasspathFile = defaultClasspathFile;
    }

    public String defaultFile() {
        return defaultClasspathFile;
    }
}