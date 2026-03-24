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

class ProcessFlowEngineTwoTest {

    private static ProcessFlowEngine pfEngine;

    @BeforeAll
    static void setup() throws Exception {
        URL processFlowTwoURL = ProcessFlowEngineOneTest.class.getClassLoader().getResource("two/process-flow.json");
        String processFlowTwoPath = Path.of(processFlowTwoURL.toURI()).toString();

        URL processFlowStatusTwoURL = ProcessFlowEngineOneTest.class.getClassLoader().getResource("two/process-flow-status.json");
        String processFlowStatusTwoPath = Path.of(processFlowStatusTwoURL.toURI()).toString();

        ProcessFlowConfig config = ProcessFlowConfig.builder()
                .processConfigPath(processFlowTwoPath)
                .statusConfigPath(processFlowStatusTwoPath)
                .connectionString("mongodb://localhost:27017/")
                .databaseName("process-flow-engine-test")
                .build();

        pfEngine = new ProcessFlowEngine(config);
    }

    /**
     * Happy path:
     * L1 APPROVE → L2 APPROVE → L4 APPROVE
     */
    @Test
    void testCompleteHappyPathApproval() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);
        assertNotNull(instanceId);

        // Level 1 actions
        List<String> actions = pfEngine.getAvailableActions(instanceId);
        assertTrue(actions.contains("APPROVE"));
        assertTrue(actions.contains("REJECT"));
        assertTrue(actions.contains("RETURN"));

        // L1 APPROVE
//        ProcessInstance pi = pfEngine.performAction(instanceId, "APPROVE", "manager@company.com", "Initial approval");
        
        String  result = pfEngine.performAction(instanceId, "APPROVE", "manager@company.com", "Initial approval");
		ProcessInstance pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);

        assertEquals(20, pi.getCurrentStatus());
        
        
        // Now at level 2

        // L2 ESCALATE (must happen from status 20, before L2 APPROVE)
        
//        pi = pfEngine.performAction(instanceId, "ESCALATE", "tech@company.com", "Escalated");
        
        result = pfEngine.performAction(instanceId, "ESCALATE", "tech@company.com", "Escalated");
		pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);

        assertEquals(25, pi.getCurrentStatus());
        // Now at level 3

        // L3 APPROVE
//        pi = pfEngine.performAction(instanceId, "APPROVE", "director@company.com", "Senior approved");
		result = pfEngine.performAction(instanceId, "APPROVE", "director@company.com", "Senior approved");
		pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);
        assertEquals(40, pi.getCurrentStatus());
        // Now at level 4

        // L4 APPROVE (final)
//        pi = pfEngine.performAction(instanceId, "APPROVE", "ceo@company.com", "Final approval");
        result = pfEngine.performAction(instanceId, "APPROVE", "ceo@company.com", "Final approval");
		pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);
        assertEquals(60, pi.getCurrentStatus());
        assertNotNull(pfEngine.getCompletedProcessInstance(instanceId));
    }

    /**
     * Rejection at Level 1 should immediately terminate
     */
    @Test
    void testLevel1RejectionTerminatesProcess() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);

       String result = pfEngine.performAction(
               instanceId, "REJECT", "manager@company.com", "Rejected at L1"
               );
       ProcessInstance pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);
//        ProcessInstance pi = pfEngine.performAction(
//                instanceId, "REJECT", "manager@company.com", "Rejected at L1"
//        );

        assertEquals(90, pi.getCurrentStatus());

        List<String> actions = pfEngine.getAvailableActions(instanceId);
        assertTrue(actions.isEmpty());
    }

    /**
     * Rework flow:
     * RETURN → RESUBMIT → APPROVE
     */
    @Test
    void testReturnAndResubmitFlow() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);

        // Level 1 return
        String result = pfEngine.performAction(
        		 instanceId, "RETURN", "manager@company.com", "Need more info"
                );
        ProcessInstance pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);
//        ProcessInstance pi = pfEngine.performAction(
//                instanceId, "RETURN", "manager@company.com", "Need more info"
//        );
        assertEquals(15, pi.getCurrentStatus());

        // Only RESUBMIT should be allowed
        List<String> actions = pfEngine.getAvailableActions(instanceId);
        assertEquals(1, actions.size());
        assertEquals("RESUBMIT", actions.get(0));

        // Resubmit
        result = pfEngine.performAction(
        		instanceId, "RESUBMIT", "user@company.com", "Updated details"
               );
       pi = pfEngine.getObjectMapper().convertValue(result, ProcessInstance.class);
//        pi = pfEngine.performAction(
//                instanceId, "RESUBMIT", "user@company.com", "Updated details"
//        );
        assertEquals(10, pi.getCurrentStatus());
        assertEquals(1, pi.getCurrentLevel());
    }

    /**
     * Invalid action should throw exception
     */
    @Test
    void testInvalidActionThrowsException() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);

        ProcessFlowException ex = assertThrows(
                ProcessFlowException.class,
                () -> pfEngine.performAction(
                        instanceId, "ESCALATE", "manager@company.com", "Invalid at L1"
                )
        );

        assertTrue(ex.getMessage().contains("Invalid action"));
    }

    /**
     * Cannot act on completed process
     */
    @Test
    void testActionOnCompletedProcessFails() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);

        pfEngine.performAction(instanceId, "REJECT", "manager@company.com", "Rejected");

        assertThrows(
                ProcessFlowException.class,
                () -> pfEngine.performAction(
                        instanceId, "APPROVE", "someone@company.com", "Should fail"
                )
        );
    }

    /**
     * Validate audit history is captured correctly
     */
    @Test
    void testProcessHistoryIsRecorded() throws Exception {
        String instanceId = pfEngine.createProcessInstance("p200", "user@company.com",
                null);

        pfEngine.performAction(instanceId, "APPROVE", "manager@company.com", "OK");
        pfEngine.performAction(instanceId, "ESCALATE", "tech@company.com", "Escalated");

        ProcessInstance pi = pfEngine.getProcessInstance(instanceId);

        assertEquals(3, pi.getHistory().size());
        assertEquals("CREATED", pi.getHistory().get(0).getAction());
        assertEquals("APPROVE", pi.getHistory().get(1).getAction());
        assertEquals("ESCALATE", pi.getHistory().get(2).getAction());
    }
}