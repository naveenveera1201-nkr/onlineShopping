package com.configs.registries;

import java.util.Collection;

import com.exceptions.ProcessFlowException;

public interface ConfigRegistry<T, K> {

    T get(K key) throws ProcessFlowException;

    T add(K key, T value) throws ProcessFlowException;

    boolean exists(K key);

    Collection<T> getAll();

    boolean clearAll();
}