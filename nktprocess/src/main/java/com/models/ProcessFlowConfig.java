package com.models;

public final class ProcessFlowConfig {

    private final String processConfigPath;
    private final String statusConfigPath;
    private final String connectionString;
    private final String databaseName;
    private final int connectionPoolSize;
    private final int connectionTimeoutMs;
    private final int socketTimeoutMs;
    private final boolean enableOptimisticLocking;
    private final int retryAttempts;
    private final int retryDelayMs;

    ProcessFlowConfig(Builder builder) {
        this.processConfigPath = builder.processConfigPath;
        this.statusConfigPath = builder.statusConfigPath;
        this.connectionString = builder.connectionString;
        this.databaseName = builder.databaseName;
        this.connectionPoolSize = builder.connectionPoolSize;
        this.connectionTimeoutMs = builder.connectionTimeoutMs;
        this.socketTimeoutMs = builder.socketTimeoutMs;
        this.enableOptimisticLocking = builder.enableOptimisticLocking;
        this.retryAttempts = builder.retryAttempts;
        this.retryDelayMs = builder.retryDelayMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters only
    public String getProcessConfigPath() {
        return processConfigPath;
    }

    public String getStatusConfigPath() {
        return statusConfigPath;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public boolean isEnableOptimisticLocking() {
        return enableOptimisticLocking;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public int getRetryDelayMs() {
        return retryDelayMs;
    }

    public static final class Builder {

        private String processConfigPath = "";
        private String statusConfigPath = "";
        private String connectionString = "mongodb://localhost:27017";
        private String databaseName = "processflow_db";
        private int connectionPoolSize = 50;
        private int connectionTimeoutMs = 10000;
        private int socketTimeoutMs = 30000;
        private boolean cacheEnabled = true;
        private int cacheExpirySeconds = 3600;
        private boolean enableOptimisticLocking = true;
        private int retryAttempts = 3;
        private int retryDelayMs = 100;

        private Builder() {
        }

        public Builder processConfigPath(String path) {
            this.processConfigPath = path;
            return this;
        }

        public Builder statusConfigPath(String path) {
            this.statusConfigPath = path;
            return this;
        }

        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder connectionPoolSize(int size) {
            this.connectionPoolSize = size;
            return this;
        }

        public Builder connectionTimeoutMs(int timeoutMs) {
            this.connectionTimeoutMs = timeoutMs;
            return this;
        }

        public Builder socketTimeoutMs(int timeoutMs) {
            this.socketTimeoutMs = timeoutMs;
            return this;
        }

        public Builder enableOptimisticLocking(boolean enabled) {
            this.enableOptimisticLocking = enabled;
            return this;
        }

        public Builder retryAttempts(int attempts) {
            this.retryAttempts = attempts;
            return this;
        }

        public Builder retryDelayMs(int delayMs) {
            this.retryDelayMs = delayMs;
            return this;
        }

        public ProcessFlowConfig build() {
            validate();
            return new ProcessFlowConfig(this);
        }

        private void validate() {
            if (connectionPoolSize <= 0) {
                throw new IllegalArgumentException("connectionPoolSize must be > 0");
            }
            if (retryAttempts < 0) {
                throw new IllegalArgumentException("retryAttempts cannot be negative");
            }
            if (retryDelayMs < 0) {
                throw new IllegalArgumentException("retryDelayMs cannot be negative");
            }
        }
    }
}