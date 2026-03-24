package com.testApplication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ProcessFlowEngine;
import com.exceptions.ProcessFlowException;
import com.models.ProcessFlowConfig;
import com.models.ProcessInstance;

class ProcessFlowEngineOneTest {

    private static ProcessFlowEngine pfEngine;

    @BeforeAll
    static void setup() throws Exception {
        URL processFlowOneURL = ProcessFlowEngineOneTest.class.getClassLoader().getResource("one/process-flow.json");
        String processFlowOnePath = Path.of(processFlowOneURL.toURI()).toString();

        URL processFlowStatusOneURL = ProcessFlowEngineOneTest.class.getClassLoader().getResource("one/process-flow-status.json");
        String processFlowStatusOnePath = Path.of(processFlowStatusOneURL.toURI()).toString();

        ProcessFlowConfig config = ProcessFlowConfig.builder()
                .processConfigPath(processFlowOnePath)
                .statusConfigPath(processFlowStatusOnePath)
                .connectionString("mongodb://localhost:27017/")
                .databaseName("process-flow-engine-test")
                .build();

        pfEngine = new ProcessFlowEngine(config);
    }

    @Test
    void shouldReturnLevelOneActionsForP100() throws ProcessFlowException {

        String instanceId = pfEngine.createProcessInstance(
                "p100",
                "user1@company.com",
                null
        );

        List<String> actions = pfEngine.getAvailableActions(instanceId);

        assertEquals(3, actions.size());
        assertTrue(actions.containsAll(List.of(
                "Approve", "Reject", "Return"
        )));
    }

    @Test
    void shouldMoveToLevelTwoAfterLevelOneApproval() throws ProcessFlowException {

        String instanceId = pfEngine.createProcessInstance(
                "p100",
                "user2@company.com",
                null
        );

		String result = pfEngine.performAction(instanceId, "Approve", "manager@company.com", "L1 approved");
        
				ProcessInstance updated = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);

        assertEquals(30, updated.getCurrentStatus());
        assertEquals(1, updated.getCurrentLevel());
    }

    @Test
    void shouldCompleteProcessOnTerminalStep() throws ProcessFlowException {

        String instanceId = pfEngine.createProcessInstance(
                "p100",
                "user3@company.com",
                null
        );

        // Level 1 approve
        pfEngine.performAction(
                instanceId,
                "Approve",
                "manager@company.com",
                "L1 approved"
        );

        // Level 2 approve (IsTerminal = true)
        String result = pfEngine.performAction(
                instanceId,
                "Approve",
                "director@company.com",
                "Final approval"
        );

		ProcessInstance updated = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);

		
        assertNotNull(pfEngine.getCompletedProcessInstance(instanceId));
        assertEquals(60, updated.getCurrentStatus());
    }

    @Test
    void shouldFailIfActionPerformedAfterProcessCompletion() throws ProcessFlowException {

        String instanceId = pfEngine.createProcessInstance(
                "p100",
                "user5@company.com",
                null
        );

        pfEngine.performAction(instanceId, "Approve", "manager@company.com", "L1");
        pfEngine.performAction(instanceId, "Approve", "director@company.com", "Final");

        ProcessFlowException ex = assertThrows(
                ProcessFlowException.class,
                () -> pfEngine.performAction(
                        instanceId,
                        "Reject",
                        "admin@company.com",
                        "Invalid"
                )
        );

        assertTrue(ex.getMessage().contains("Process instance not found"));
    }

    @Test
    void nonApprovalProcessShouldHaveNoActions() throws ProcessFlowException {

        String instanceId = pfEngine.createProcessInstance(
                "p101",
                "user6@company.com",
                null
        );

        List<String> availableActions =  pfEngine.getAvailableActions(instanceId);
        assertEquals(0, availableActions.size());
    }
}