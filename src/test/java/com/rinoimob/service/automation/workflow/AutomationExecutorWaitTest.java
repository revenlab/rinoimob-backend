package com.rinoimob.service.automation.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.dto.WorkflowConfigDto;
import com.rinoimob.domain.dto.WorkflowEdgeDto;
import com.rinoimob.domain.dto.WorkflowNodeDto;
import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.enums.ActionType;
import com.rinoimob.domain.enums.NodeType;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.service.automation.ActionHandler;
import com.rinoimob.service.automation.ActionHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationExecutorWaitTest {

    @Mock
    private AutomationExecutionRepository automationExecutionRepository;

    @Mock
    private AutomationWorkflowRepository workflowRepository;

    @Mock
    private ActionHandlerRegistry actionHandlerRegistry;

    @Mock
    private WorkflowWaitService workflowWaitService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AutomationExecutor automationExecutor;

    private ActionHandler waitActionHandler;
    private ActionHandler notifyActionHandler;

    @BeforeEach
    void setUp() {
        waitActionHandler = new com.rinoimob.service.automation.handler.WaitActionHandler(workflowWaitService);
        notifyActionHandler = mock(ActionHandler.class);

        automationExecutor = new AutomationExecutor(
                automationExecutionRepository,
                workflowRepository,
                objectMapper,
                actionHandlerRegistry);

        lenient().when(actionHandlerRegistry.getHandler(eq(ActionType.WAIT))).thenReturn(waitActionHandler);
        lenient().when(actionHandlerRegistry.getHandler(eq(ActionType.SEND_NOTIFICATION))).thenReturn(notifyActionHandler);
    }

    @Test
    void shouldPauseWorkflowAsynchronouslyWhenWaitActionIsReached() throws Exception {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID workflowId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID executionId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        LocalDateTime resumeAt = LocalDateTime.now().plusSeconds(5);

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(workflowId);
        workflow.setTenantId(tenantId);
        workflow.setWorkflowConfig(objectMapper.writeValueAsString(new WorkflowConfigDto(
                List.of(
                        new WorkflowNodeDto("trigger-1", NodeType.TRIGGER, null, Map.<String, Object>of("triggerType", "LEAD_CREATED")),
                        new WorkflowNodeDto("wait-1", NodeType.ACTION, null, Map.<String, Object>of(
                                "actionType", "WAIT",
                                "delaySeconds", 5
                        ))
                ),
                List.of(new WorkflowEdgeDto("trigger-1", "wait-1", null))
        )));

        when(automationExecutionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            AutomationExecution execution = invocation.getArgument(0);
            execution.setId(executionId);
            return execution;
        });
        when(automationExecutionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(workflowWaitService.scheduleResume(eq(executionId), eq(5L))).thenReturn(resumeAt);

        Map<String, Object> triggerData = new HashMap<>();
        triggerData.put("leadId", "lead-1");

        var response = automationExecutor.executeWorkflow(workflow, "LEAD_CREATED", triggerData);

        ArgumentCaptor<AutomationExecution> executionCaptor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(automationExecutionRepository).save(executionCaptor.capture());
        AutomationExecution savedExecution = executionCaptor.getValue();

        assertThat(response.getStatus()).isEqualTo(WorkflowExecutionStatus.WAITING);
        assertThat(savedExecution.getStatus()).isEqualTo(WorkflowExecutionStatus.WAITING);
        assertThat(savedExecution.getResumeAt()).isEqualTo(resumeAt);
        assertThat(response.getResumeAt()).isEqualTo(resumeAt);
        assertThat(response.getResultData()).containsEntry("wait_scheduled", true);
        assertThat(response.getExecutionPath()).containsExactly("trigger-1", "wait-1");
        verify(workflowWaitService).scheduleResume(eq(executionId), eq(5L));
        verifyNoInteractions(notifyActionHandler);
    }

    @Test
    void shouldResumeWaitingWorkflowAndCompleteDownstreamActions() throws Exception {
        UUID tenantId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID workflowId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID executionId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(workflowId);
        workflow.setTenantId(tenantId);
        workflow.setWorkflowConfig(objectMapper.writeValueAsString(new WorkflowConfigDto(
                List.of(
                        new WorkflowNodeDto("trigger-1", NodeType.TRIGGER, null, Map.<String, Object>of("triggerType", "LEAD_CREATED")),
                        new WorkflowNodeDto("wait-1", NodeType.ACTION, null, Map.<String, Object>of(
                                "actionType", "WAIT",
                                "delaySeconds", 5
                        )),
                        new WorkflowNodeDto("notify-1", NodeType.ACTION, null, Map.<String, Object>of(
                                "actionType", "SEND_NOTIFICATION",
                                "title", "Done",
                                "message", "Finished"
                        ))
                ),
                List.of(
                        new WorkflowEdgeDto("trigger-1", "wait-1", null),
                        new WorkflowEdgeDto("wait-1", "notify-1", null)
                )
        )));

        AutomationExecution waitingExecution = new AutomationExecution();
        waitingExecution.setId(executionId);
        waitingExecution.setWorkflowId(workflowId);
        waitingExecution.setTenantId(tenantId);
        waitingExecution.setTriggerEvent("LEAD_CREATED");
        waitingExecution.setStatus(WorkflowExecutionStatus.WAITING);
        waitingExecution.setTriggerData("{\"leadId\":\"lead-1\"}");
        waitingExecution.setExecutionPath("[\"trigger-1\",\"wait-1\"]");
        waitingExecution.setResultData("{\"waited\":5,\"wait_scheduled\":true,\"wait_resume_at\":\""
                + LocalDateTime.now().plusSeconds(5) + "\"}");

        when(automationExecutionRepository.findById(executionId)).thenReturn(java.util.Optional.of(waitingExecution));
        when(workflowRepository.findByTenantIdAndId(tenantId, workflowId)).thenReturn(java.util.Optional.of(workflow));
        when(automationExecutionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            Map<String, Object> resultData = invocation.getArgument(2);
            resultData.put("notification_sent", true);
            resultData.put("notification_title", "Done");
            return null;
        }).when(notifyActionHandler).execute(anyMap(), anyMap(), anyMap());

        var response = automationExecutor.resumeWaitingExecution(executionId);

        ArgumentCaptor<AutomationExecution> executionCaptor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(automationExecutionRepository, atLeast(1)).save(executionCaptor.capture());
        AutomationExecution finalSavedExecution = executionCaptor.getAllValues()
                .get(executionCaptor.getAllValues().size() - 1);

        assertThat(response.getStatus()).isEqualTo(WorkflowExecutionStatus.COMPLETED);
        assertThat(finalSavedExecution.getStatus()).isEqualTo(WorkflowExecutionStatus.COMPLETED);
        assertThat(response.getExecutionPath()).containsExactly("trigger-1", "wait-1", "notify-1");
        assertThat(response.getResultData()).containsEntry("notification_sent", true);
        verify(notifyActionHandler).execute(anyMap(), anyMap(), anyMap());
    }
}
