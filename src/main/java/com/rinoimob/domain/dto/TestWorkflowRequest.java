package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestWorkflowRequest {
    private String triggerEvent;
    private Map<String, Object> triggerData;
}
