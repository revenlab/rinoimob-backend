package com.rinoimob.service.automation.handler;

import com.rinoimob.service.automation.workflow.WorkflowWaitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitActionHandlerTest {

    @Mock
    private WorkflowWaitService workflowWaitService;

    @InjectMocks
    private WaitActionHandler handler;

    @Test
    void shouldScheduleWaitWithoutBlocking() {
        UUID executionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        LocalDateTime resumeAt = LocalDateTime.now().plusSeconds(30);

        Map<String, Object> actionData = new HashMap<>();
        actionData.put("delaySeconds", 30);
        actionData.put("_executionId", executionId);

        Map<String, Object> context = new HashMap<>();
        Map<String, Object> resultData = new HashMap<>();

        when(workflowWaitService.scheduleResume(eq(executionId), eq(30L))).thenReturn(resumeAt);

        assertTimeoutPreemptively(java.time.Duration.ofMillis(100),
                () -> handler.execute(actionData, context, resultData));

        verify(workflowWaitService).scheduleResume(eq(executionId), eq(30L));
        assertThat(resultData).containsEntry("waited", 30L);
        assertThat(resultData).containsEntry("wait_scheduled", true);
        assertThat(resultData).containsEntry("wait_resume_at", resumeAt.toString());
    }
}
