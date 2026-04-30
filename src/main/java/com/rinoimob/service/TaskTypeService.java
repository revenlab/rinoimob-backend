package com.rinoimob.service;

import com.rinoimob.domain.dto.CreateTaskTypeRequest;
import com.rinoimob.domain.dto.TaskTypeResponse;
import com.rinoimob.domain.dto.UpdateTaskTypeRequest;
import com.rinoimob.domain.entity.TaskType;
import com.rinoimob.domain.repository.TaskTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskTypeService {

    private final TaskTypeRepository taskTypeRepository;

    public List<TaskTypeResponse> list(UUID tenantId) {
        return taskTypeRepository.findAvailableForTenant(tenantId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TaskTypeResponse create(UUID tenantId, CreateTaskTypeRequest req) {
        TaskType type = new TaskType();
        type.setTenantId(tenantId);
        type.setName(req.name());
        type.setColor(req.color() != null ? req.color() : "#6366f1");
        type.setIcon(req.icon());
        type.setPosition(req.position() != null ? req.position() : 99);
        type.setActive(true);
        return toResponse(taskTypeRepository.save(type));
    }

    public TaskTypeResponse update(UUID id, UUID tenantId, UpdateTaskTypeRequest req) {
        TaskType type = taskTypeRepository.findById(id)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task type not found or not editable"));
        if (req.name() != null) type.setName(req.name());
        if (req.color() != null) type.setColor(req.color());
        if (req.icon() != null) type.setIcon(req.icon());
        if (req.position() != null) type.setPosition(req.position());
        if (req.active() != null) type.setActive(req.active());
        return toResponse(taskTypeRepository.save(type));
    }

    public void delete(UUID id, UUID tenantId) {
        TaskType type = taskTypeRepository.findById(id)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task type not found or not deletable"));
        type.setActive(false);
        taskTypeRepository.save(type);
    }

    private TaskTypeResponse toResponse(TaskType t) {
        return new TaskTypeResponse(t.getId(), t.getName(), t.getColor(), t.getIcon(), t.getPosition(), t.getTenantId() == null);
    }
}
