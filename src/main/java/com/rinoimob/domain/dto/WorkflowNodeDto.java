package com.rinoimob.domain.dto;

import com.rinoimob.domain.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeDto {
    private String id;
    private NodeType type;
    private Map<String, Double> position;
    private Map<String, Object> data;
}
