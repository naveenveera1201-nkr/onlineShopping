package com.configs.validations;

import java.util.*;
import java.util.stream.Collectors;

import com.CommonUtils;
import com.exceptions.ConfigValidationException;
import com.models.ApprovalStep;
import com.models.Process;

public class ProcessConfigValidator implements ConfigValidator<Process> {

    public ProcessConfigValidator() {}

    @Override
    public void validate(Process process) throws ConfigValidationException {

        if (process == null) {
            throw new ConfigValidationException("Process config cannot be null");
        }

        validateBasicFields(process);

        if (Boolean.TRUE.equals(process.getApprovalFlow())) {
            validateApprovalFlow(process);
        } else {
            validateNonApprovalFlow(process);
        }
    }

    private void validateBasicFields(Process process) throws ConfigValidationException {

        if (CommonUtils.isBlank(process.getCode())) {
            throw new ConfigValidationException("Process code is required");
        }

        if (process.getInitialStatus() == null) {
            throw new ConfigValidationException(
                    "InitialStatus is required for process: " + process.getCode());
        }
    }

    private void validateApprovalFlow(Process process) throws ConfigValidationException {

        if (process.getNoOfLevels() == null || process.getNoOfLevels() < 1) {
            throw new ConfigValidationException(
                    "NoOfLevels must be >= 1 for approval flow process: " + process.getCode());
        }

        List<ApprovalStep> steps = process.getApprovalSteps();
        if (steps == null || steps.isEmpty()) {
            throw new ConfigValidationException(
                    "ApprovalSteps must be defined for approval flow process: " + process.getCode());
        }

        validateApprovalSteps(process, steps);
    }

    private void validateApprovalSteps(Process process, List<ApprovalStep> steps) throws ConfigValidationException {

        // Group by level
        Map<Integer, List<ApprovalStep>> stepsByLevel =
                steps.stream().collect(Collectors.groupingBy(ApprovalStep::getLevel));

        for (int level = 1; level <= process.getNoOfLevels(); level++) {
            if (!stepsByLevel.containsKey(level)) {
                throw new ConfigValidationException(
                        "Missing approval steps for level " + level +
                                " in process: " + process.getCode());
            }
        }

        // Validate each step
        Set<String> uniqueKeys = new HashSet<>();

        for (ApprovalStep step : steps) {

            // TODO: can code of approval step not be unique? (required for validation)
            // TODO: can approval steps level be in negative? (required for validation)
            if (step.getLevel() == null || step.getLevel() < 1) {
                throw new ConfigValidationException(
                        "Invalid level in approval step for process: " + process.getCode());
            }

            if (CommonUtils.isBlank(step.getAction())) {
                throw new ConfigValidationException(
                        "Action is required in approval step for process: " + process.getCode());
            }

            if (step.getPrevStatus() == null || step.getNextStatus() == null) {
                throw new ConfigValidationException(
                        "PrevStatus and NextStatus are required in approval step for process: "
                                + process.getCode());
            }

            // Ensure no duplicate (level + action + prevStatus)
            String key = step.getLevel() + "|" + step.getAction() + "|" + step.getPrevStatus();
            if (!uniqueKeys.add(key)) {
                throw new ConfigValidationException(
                        "Duplicate approval step detected: " + key +
                                " in process: " + process.getCode());
            }
        }
    }

    private void validateNonApprovalFlow(Process process) throws ConfigValidationException {

        if (process.getApprovalSteps() != null && !process.getApprovalSteps().isEmpty()) {
            throw new ConfigValidationException(
                    "ApprovalSteps must not be defined when ApprovalFlow is false for process: "
                            + process.getCode());
        }
    }
}