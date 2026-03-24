package com.configs.registries.status;

import java.util.Collection;
import java.util.Collections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.exceptions.ProcessFlowException;
import com.models.Status;

public class InMemoryStatusRegistry
        implements StatusRegistry {

    private final Map<Integer, Status> cache;

    public InMemoryStatusRegistry() {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Status get(Integer code) throws ProcessFlowException {
        Status s = cache.get(code);
        if (s == null) {
            throw new ProcessFlowException("Status not found: " + code);
        }
        return s;
    }

    @Override
    public boolean exists(Integer code) {
        return cache.containsKey(code);
    }

    @Override
    public Status add(Integer key, Status val) {
        cache.put(key, val);
        return val;
    }

    @Override
    public Collection<Status> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public String getDescription(Integer code) throws ProcessFlowException {
        return get(code).getDescription();
    }

    @Override
    public boolean clearAll() {
        boolean modified = !cache.isEmpty();
        cache.clear();
        return modified;
    }
}