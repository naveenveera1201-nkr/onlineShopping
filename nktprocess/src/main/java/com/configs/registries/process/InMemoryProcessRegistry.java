package com.configs.registries.process;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.exceptions.ProcessFlowException;
import com.models.ApprovalStep;
import com.models.Process;

public class InMemoryProcessRegistry implements ProcessRegistry {

    private final Map<String, Process> cache;

    public InMemoryProcessRegistry() {
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Process get(String code) throws ProcessFlowException {
        Process p = cache.get(code);
        if (p == null) {
            throw new ProcessFlowException("Process not found: " + code);
        }
        return p;
    }

    @Override
    public Process add(String key, Process value) {
        cache.put(key, value);
        return value;
    }

    @Override
    public boolean exists(String code) {
        return cache.containsKey(code);
    }

    @Override
    public Collection<Process> getAll() {
        return Collections.unmodifiableCollection(cache.values());
    }

    @Override
    public boolean isApprovalFlow(String processCode) throws ProcessFlowException {
        return Boolean.TRUE.equals(get(processCode).getApprovalFlow());
    }

    @Override
    public List<ApprovalStep> getApprovalSteps(String processCode) throws ProcessFlowException {
        return Collections.unmodifiableList(get(processCode).getApprovalSteps());
    }

    @Override
    public boolean clearAll() {
        boolean modified = !cache.isEmpty();
        cache.clear();
        return modified;
    }
}
