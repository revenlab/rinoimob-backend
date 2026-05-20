package com.rinoimob.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rinoimob.domain.entity.AutomationWorkflow;
import com.rinoimob.domain.entity.Lead;
import com.rinoimob.domain.enums.LeadStatus;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import com.rinoimob.domain.repository.AutomationWorkflowRepository;
import com.rinoimob.domain.repository.LeadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadNoActivitySchedulerTest {

    @Mock
    private AutomationWorkflowRepository workflowRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private AutomationExecutionRepository automationExecutionRepository;

    @Mock
    private AutomationExecutor automationExecutor;

    private LeadNoActivityScheduler scheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        scheduler = new LeadNoActivityScheduler(
                workflowRepository,
                leadRepository,
                automationExecutionRepository,
                automationExecutor,
                objectMapper);
    }

    @Test
    void shouldDispatchInactiveLeadWhenNoPreviousExecutionExists() {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID workflowId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID leadId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(10);

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(workflowId);
        workflow.setTenantId(tenantId);
        workflow.setIsActive(true);
        workflow.setWorkflowConfig("""
                {
                  "nodes": [
                    {
                      "id": "trigger-1",
                      "type": "TRIGGER",
                      "data": {
                        "triggerType": "LEAD_NO_ACTIVITY",
                        "parameters": { "inactiveDays": 5 }
                      }
                    }
                  ],
                  "edges": []
                }
                """);

        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setTenantId(tenantId);
        lead.setName("Lead Teste");
        lead.setStatus(LeadStatus.NEW);
        lead.setUpdatedAt(updatedAt);

        when(workflowRepository.findAll()).thenReturn(List.of(workflow));
        when(leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(eq(tenantId), anyList(), any()))
                .thenReturn(List.of(lead));
        when(automationExecutionRepository.existsLeadTriggerExecutionAfter(
                eq(workflowId),
                eq("LEAD_NO_ACTIVITY"),
                eq(leadId.toString()),
                eq(updatedAt)))
                .thenReturn(false);

        scheduler.scanInactiveLeads();

        ArgumentCaptor<Map<String, Object>> triggerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(automationExecutor).executeWorkflow(eq(workflow), eq("LEAD_NO_ACTIVITY"), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue()).containsEntry("leadId", leadId.toString());
        assertThat(triggerCaptor.getValue()).containsEntry("inactiveDays", 5);
        assertThat(triggerCaptor.getValue()).containsKey("inactiveSince");
        verifyNoMoreInteractions(automationExecutor);
    }

    @Test
    void shouldSkipInactiveLeadWhenExecutionAlreadyExists() {
        UUID tenantId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID workflowId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID leadId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        LocalDateTime updatedAt = LocalDateTime.now().minusDays(8);

        AutomationWorkflow workflow = new AutomationWorkflow();
        workflow.setId(workflowId);
        workflow.setTenantId(tenantId);
        workflow.setIsActive(true);
        workflow.setWorkflowConfig("""
                {
                  "nodes": [
                    {
                      "id": "trigger-1",
                      "type": "TRIGGER",
                      "data": { "triggerType": "LEAD_NO_ACTIVITY" }
                    }
                  ],
                  "edges": []
                }
                """);

        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setTenantId(tenantId);
        lead.setName("Lead Teste");
        lead.setStatus(LeadStatus.NEW);
        lead.setUpdatedAt(updatedAt);

        when(workflowRepository.findAll()).thenReturn(List.of(workflow));
        when(leadRepository.findByTenantIdAndDeletedAtIsNullAndStatusNotInAndUpdatedAtBefore(eq(tenantId), anyList(), any()))
                .thenReturn(List.of(lead));
        when(automationExecutionRepository.existsLeadTriggerExecutionAfter(
                eq(workflowId),
                eq("LEAD_NO_ACTIVITY"),
                eq(leadId.toString()),
                eq(updatedAt)))
                .thenReturn(true);

        scheduler.scanInactiveLeads();

        verify(automationExecutor, never()).executeWorkflow(any(), anyString(), anyMap());
    }
}
