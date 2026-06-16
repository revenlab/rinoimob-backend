package com.rinoimob.service.automation.workflow;

import com.rinoimob.domain.entity.AutomationExecution;
import com.rinoimob.domain.enums.WorkflowExecutionStatus;
import com.rinoimob.domain.repository.AutomationExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowWaitRecoveryScheduler {

    private final AutomationExecutionRepository automationExecutionRepository;
    private final AutomationExecutor automationExecutor;

    @Scheduled(fixedDelayString = "${automation.wait-recovery-scan-ms:30000}")
    public void resumeDueExecutions() {
        List<AutomationExecution> executions = automationExecutionRepository
                .findByStatusAndResumeAtLessThanEqualOrderByResumeAtAsc(WorkflowExecutionStatus.WAITING, LocalDateTime.now());

        for (AutomationExecution execution : executions) {
            try {
                automationExecutor.resumeWaitingExecution(execution.getId());
            } catch (Exception e) {
                log.error("Error recovering waiting execution {}: {}", execution.getId(), e.getMessage(), e);
            }
        }
    }
}
