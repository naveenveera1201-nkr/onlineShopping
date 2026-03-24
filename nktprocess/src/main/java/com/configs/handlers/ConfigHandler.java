package com.configs.handlers;

import java.io.InputStream;

import com.configs.ConfigType;

public interface ConfigHandler {
    ConfigType type();
    void load(InputStream input) throws Exception;
}