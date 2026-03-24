package com.configs.registries.process;

import java.util.List;

import com.configs.registries.ConfigRegistry;
import com.exceptions.ProcessFlowException;
import com.models.ApprovalStep;
import com.models.Process;

public interface ProcessRegistry extends ConfigRegistry<Process, String> {

    // Process-specific queries
    boolean isApprovalFlow(String processCode) throws ProcessFlowException;

    List<ApprovalStep> getApprovalSteps(String processCode) throws ProcessFlowException;
}