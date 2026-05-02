package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowConfigDto {
    private List<WorkflowNodeDto> nodes;
    private List<WorkflowEdgeDto> edges;
}
