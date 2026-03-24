package com.configs.registries.status;

import com.configs.registries.ConfigRegistry;
import com.exceptions.ProcessFlowException;
import com.models.Status;

public interface StatusRegistry extends ConfigRegistry<Status, Integer> {

    Status add(Integer key, Status val);

    String getDescription(Integer statusCode) throws ProcessFlowException;
}