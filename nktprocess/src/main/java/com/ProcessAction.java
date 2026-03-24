package com;

public enum ProcessAction {
    CREATED(1, "created"),
    UPDATED(2, "updated"),
    DELETED(3, "deleted");

    private int code;
    private String key;

    ProcessAction(int code, String key) {
        this.code = code;
        this.key = key;
    }

    public int getCode() {
        return code;
    }

    public String getKey() {
        return key;
    }
}
