package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.domain.dto.UpdateTaskRequest;
import com.rinoimob.domain.entity.Task;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.TaskRepository;
import com.rinoimob.domain.repository.TaskTypeRepository;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.automation.workflow.AutomationEventDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private UserRepository userRepository;
    @Mock private TaskTypeRepository taskTypeRepository;
    @Mock private AutomationEventDispatcher automationEventDispatcher;

    @Test
    void listWithScopedBrokerUsesAssignedToFilter() {
        UUID tenantId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        when(taskRepository.findByTenantIdAndAssignedToAndDeletedAtIsNull(
                org.mockito.Mockito.eq(tenantId),
                org.mockito.Mockito.eq(brokerId),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildTask(tenantId, brokerId))));

        TaskService service = buildService();

        assertThat(service.list(tenantId, brokerId, null, null, 0, 20).getTotalElements()).isEqualTo(1);
        verify(taskRepository).findByTenantIdAndAssignedToAndDeletedAtIsNull(
                org.mockito.Mockito.eq(tenantId),
                org.mockito.Mockito.eq(brokerId),
                any(Pageable.class));
    }

    @Test
    void createWithScopedBrokerAssignsToCurrentUser() {
        UUID tenantId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            return task;
        });

        TaskService service = buildService();

        service.create(tenantId, brokerId, new CreateTaskRequest("Follow-up", null, null, null, null, null));

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedTo()).isEqualTo(brokerId);
    }

    @Test
    void createWithScopedBrokerRejectsAssignmentToAnotherUser() {
        UUID tenantId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        UUID otherBrokerId = UUID.randomUUID();

        TaskService service = buildService();

        assertThatThrownBy(() -> service.create(
                tenantId,
                brokerId,
                new CreateTaskRequest("Follow-up", null, null, otherBrokerId, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot assign task to another broker");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateWithScopedBrokerRejectsUnassignedTask() {
        UUID tenantId = UUID.randomUUID();
        UUID brokerId = UUID.randomUUID();
        Task task = buildTask(tenantId, UUID.randomUUID());
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        TaskService service = buildService();

        assertThatThrownBy(() -> service.update(task.getId(), tenantId, brokerId,
                new UpdateTaskRequest("New", null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Access denied to this task");
    }

    private TaskService buildService() {
        return new TaskService(taskRepository, leadRepository, userRepository, taskTypeRepository, automationEventDispatcher);
    }

    private Task buildTask(UUID tenantId, UUID assignedTo) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTenantId(tenantId);
        task.setAssignedTo(assignedTo);
        task.setTitle("Follow-up");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
