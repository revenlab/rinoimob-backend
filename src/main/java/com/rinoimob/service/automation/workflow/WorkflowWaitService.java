package com.rinoimob.service.automation.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowWaitService {

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<AutomationExecutor> automationExecutorProvider;

    public LocalDateTime scheduleResume(UUID executionId, long delaySeconds) {
        LocalDateTime resumeAt = LocalDateTime.now().plusSeconds(delaySeconds);
        Instant resumeInstant = resumeAt.atZone(ZoneId.systemDefault()).toInstant();

        Runnable resumeTask = () -> {
            try {
                automationExecutorProvider.getObject().resumeWaitingExecution(executionId);
            } catch (Exception e) {
                log.error("Error resuming waiting execution {}: {}", executionId, e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskScheduler.schedule(resumeTask, resumeInstant);
                }
            });
        } else {
            taskScheduler.schedule(resumeTask, resumeInstant);
        }

        return resumeAt;
    }
}
