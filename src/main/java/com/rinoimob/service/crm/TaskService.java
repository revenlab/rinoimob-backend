package com.rinoimob.service.crm;

import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.domain.dto.TaskResponse;
import com.rinoimob.domain.dto.UpdateTaskRequest;
import com.rinoimob.domain.entity.Task;
import com.rinoimob.domain.entity.TaskType;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.service.automation.workflow.AutomationEventDispatcher;
import com.rinoimob.domain.repository.TaskRepository;
import com.rinoimob.service.automation.workflow.AutomationEventDispatcher;
import com.rinoimob.domain.repository.TaskTypeRepository;
import com.rinoimob.service.automation.workflow.AutomationEventDispatcher;
import com.rinoimob.domain.repository.UserRepository;
import com.rinoimob.service.automation.workflow.AutomationEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final AutomationEventDispatcher automationEventDispatcher;

    public Page<TaskResponse> list(UUID tenantId, Boolean pending, UUID leadId, int page, int size) {
        return list(tenantId, null, pending, leadId, page, size);
    }

    public Page<TaskResponse> list(UUID tenantId, UUID scopedUserId, Boolean pending, UUID leadId, int page, int size) {
        validateLeadBelongsToTenant(tenantId, leadId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("dueAt").ascending().and(Sort.by("createdAt").descending()));
        Page<Task> tasks;
        if (scopedUserId != null && leadId != null) {
            tasks = taskRepository.findByTenantIdAndAssignedToAndLeadIdAndDeletedAtIsNull(tenantId, scopedUserId, leadId, pageable);
        } else if (scopedUserId != null && Boolean.TRUE.equals(pending)) {
            tasks = taskRepository.findByTenantIdAndAssignedToAndCompletedAtIsNullAndDeletedAtIsNull(tenantId, scopedUserId, pageable);
        } else if (scopedUserId != null && Boolean.FALSE.equals(pending)) {
            tasks = taskRepository.findByTenantIdAndAssignedToAndCompletedAtIsNotNullAndDeletedAtIsNull(tenantId, scopedUserId, pageable);
        } else if (scopedUserId != null) {
            tasks = taskRepository.findByTenantIdAndAssignedToAndDeletedAtIsNull(tenantId, scopedUserId, pageable);
        } else if (leadId != null) {
            tasks = taskRepository.findByTenantIdAndLeadIdAndDeletedAtIsNull(tenantId, leadId, pageable);
        } else if (Boolean.TRUE.equals(pending)) {
            tasks = taskRepository.findByTenantIdAndCompletedAtIsNullAndDeletedAtIsNull(tenantId, pageable);
        } else if (Boolean.FALSE.equals(pending)) {
            tasks = taskRepository.findByTenantIdAndCompletedAtIsNotNullAndDeletedAtIsNull(tenantId, pageable);
        } else {
            tasks = taskRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable);
        }
        return tasks.map(this::toResponse);
    }

    public List<TaskResponse> listByLead(UUID tenantId, UUID leadId) {
        return taskRepository.findByTenantIdAndLeadIdAndDeletedAtIsNull(tenantId, leadId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TaskResponse create(UUID tenantId, CreateTaskRequest req) {
        return create(tenantId, null, req);
    }

    public TaskResponse create(UUID tenantId, UUID scopedUserId, CreateTaskRequest req) {
        UUID assignedTo = req.assignedTo();
        if (scopedUserId != null) {
            if (assignedTo != null && !scopedUserId.equals(assignedTo)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign task to another broker");
            }
            assignedTo = scopedUserId;
        }
        validateLeadBelongsToTenant(tenantId, req.leadId());
        validateActiveUserBelongsToTenant(tenantId, assignedTo);
        validateTaskTypeBelongsToTenantOrGlobal(tenantId, req.taskTypeId());
        Task task = Task.builder()
                .tenantId(tenantId)
                .title(req.title())
                .description(req.description())
                .leadId(req.leadId())
                .assignedTo(assignedTo)
                .dueAt(req.dueAt())
                .taskTypeId(req.taskTypeId())
                .build();
        task = taskRepository.save(task);
        automationEventDispatcher.dispatchTaskCreated(task);
        return toResponse(task);
    }

    public TaskResponse update(UUID id, UUID tenantId, UpdateTaskRequest req) {
        return update(id, tenantId, null, req);
    }

    public TaskResponse update(UUID id, UUID tenantId, UUID scopedUserId, UpdateTaskRequest req) {
        Task task = findTask(id, tenantId);
        assertScopedAccess(task, scopedUserId);
        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.leadId() != null) {
            validateLeadBelongsToTenant(tenantId, req.leadId());
            task.setLeadId(req.leadId());
        }
        if (req.assignedTo() != null) {
            if (scopedUserId != null && !scopedUserId.equals(req.assignedTo())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign task to another broker");
            }
            validateActiveUserBelongsToTenant(tenantId, req.assignedTo());
            task.setAssignedTo(req.assignedTo());
        }
        if (req.dueAt() != null) task.setDueAt(req.dueAt());
        if (req.taskTypeId() != null) {
            validateTaskTypeBelongsToTenantOrGlobal(tenantId, req.taskTypeId());
            task.setTaskTypeId(req.taskTypeId());
        }
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse complete(UUID id, UUID tenantId) {
        return complete(id, tenantId, null);
    }

    public TaskResponse complete(UUID id, UUID tenantId, UUID scopedUserId) {
        Task task = findTask(id, tenantId);
        assertScopedAccess(task, scopedUserId);
        task.setCompletedAt(task.getCompletedAt() == null ? LocalDateTime.now() : null);
        task = taskRepository.save(task);
        if (task.getCompletedAt() != null) {
            automationEventDispatcher.dispatchTaskCompleted(task);
        }
        return toResponse(task);
    }

    public void delete(UUID id, UUID tenantId) {
        delete(id, tenantId, null);
    }

    public void delete(UUID id, UUID tenantId, UUID scopedUserId) {
        Task task = findTask(id, tenantId);
        assertScopedAccess(task, scopedUserId);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    private Task findTask(UUID id, UUID tenantId) {
        return taskRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void assertScopedAccess(Task task, UUID scopedUserId) {
        if (scopedUserId != null && !scopedUserId.equals(task.getAssignedTo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this task");
        }
    }

    private void validateLeadBelongsToTenant(UUID tenantId, UUID leadId) {
        if (leadId == null) return;
        leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(leadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    private void validateActiveUserBelongsToTenant(UUID tenantId, UUID userId) {
        if (userId == null) return;
        userRepository.findByIdAndTenantId(userId, tenantId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned user not found"));
    }

    private void validateTaskTypeBelongsToTenantOrGlobal(UUID tenantId, UUID taskTypeId) {
        if (taskTypeId == null) return;
        taskTypeRepository.findAvailableByIdForTenant(taskTypeId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task type not found"));
    }

    private TaskResponse toResponse(Task task) {
        String leadName = task.getLeadId() != null
                ? leadRepository.findByIdAndTenantIdAndDeletedAtIsNull(task.getLeadId(), task.getTenantId()).map(l -> l.getName()).orElse(null)
                : null;
        String assignedToName = task.getAssignedTo() != null
                ? userRepository.findByIdAndTenantId(task.getAssignedTo(), task.getTenantId())
                        .map(u -> (u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : ""))
                        .map(String::trim).orElse(null)
                : null;
        boolean overdue = task.getDueAt() != null
                && task.getDueAt().isBefore(LocalDateTime.now())
                && task.getCompletedAt() == null;
        TaskType taskType = task.getTaskTypeId() != null
                ? taskTypeRepository.findAvailableByIdForTenant(task.getTaskTypeId(), task.getTenantId()).orElse(null)
                : null;
        return new TaskResponse(
                task.getId(), task.getTenantId(), task.getLeadId(), leadName,
                task.getAssignedTo(), assignedToName, task.getTitle(), task.getDescription(),
                task.getDueAt(), task.getCompletedAt() != null, task.getCompletedAt(),
                task.getCreatedAt(), task.getUpdatedAt(), overdue,
                task.getTaskTypeId(),
                taskType != null ? taskType.getName() : null,
                taskType != null ? taskType.getColor() : null,
                taskType != null ? taskType.getIcon() : null
        );
    }
}
