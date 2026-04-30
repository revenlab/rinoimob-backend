package com.rinoimob.service;

import com.rinoimob.domain.dto.CreateTaskRequest;
import com.rinoimob.domain.dto.TaskResponse;
import com.rinoimob.domain.dto.UpdateTaskRequest;
import com.rinoimob.domain.entity.Task;
import com.rinoimob.domain.repository.LeadRepository;
import com.rinoimob.domain.repository.TaskRepository;
import com.rinoimob.domain.repository.UserRepository;
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

    public Page<TaskResponse> list(UUID tenantId, Boolean pending, UUID leadId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dueAt").ascending().and(Sort.by("createdAt").descending()));
        Page<Task> tasks;
        if (leadId != null) {
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
        Task task = Task.builder()
                .tenantId(tenantId)
                .title(req.title())
                .description(req.description())
                .leadId(req.leadId())
                .assignedTo(req.assignedTo())
                .dueAt(req.dueAt())
                .build();
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse update(UUID id, UUID tenantId, UpdateTaskRequest req) {
        Task task = findTask(id, tenantId);
        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.leadId() != null) task.setLeadId(req.leadId());
        if (req.assignedTo() != null) task.setAssignedTo(req.assignedTo());
        if (req.dueAt() != null) task.setDueAt(req.dueAt());
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse complete(UUID id, UUID tenantId) {
        Task task = findTask(id, tenantId);
        task.setCompletedAt(task.getCompletedAt() == null ? LocalDateTime.now() : null);
        return toResponse(taskRepository.save(task));
    }

    public void delete(UUID id, UUID tenantId) {
        Task task = findTask(id, tenantId);
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    private Task findTask(UUID id, UUID tenantId) {
        return taskRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId) && t.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private TaskResponse toResponse(Task task) {
        String leadName = task.getLeadId() != null
                ? leadRepository.findById(task.getLeadId()).map(l -> l.getName()).orElse(null)
                : null;
        String assignedToName = task.getAssignedTo() != null
                ? userRepository.findById(task.getAssignedTo())
                        .map(u -> (u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : ""))
                        .map(String::trim).orElse(null)
                : null;
        boolean overdue = task.getDueAt() != null
                && task.getDueAt().isBefore(LocalDateTime.now())
                && task.getCompletedAt() == null;
        return new TaskResponse(
                task.getId(), task.getTenantId(), task.getLeadId(), leadName,
                task.getAssignedTo(), assignedToName, task.getTitle(), task.getDescription(),
                task.getDueAt(), task.getCompletedAt() != null, task.getCompletedAt(),
                task.getCreatedAt(), task.getUpdatedAt(), overdue
        );
    }
}
