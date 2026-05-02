package com.rinoimob.service;

import com.rinoimob.domain.dto.WorkflowConfigDto;
import com.rinoimob.domain.dto.WorkflowEdgeDto;
import com.rinoimob.domain.dto.WorkflowNodeDto;
import com.rinoimob.domain.enums.NodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class WorkflowGraphValidator {

    public void validate(WorkflowConfigDto config) throws IllegalArgumentException {
        if (config == null) {
            throw new IllegalArgumentException("Workflow configuration cannot be null");
        }

        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one node");
        }

        if (config.getEdges() == null) {
            config.setEdges(new ArrayList<>());
        }

        validateTriggerNode(config);
        validateEndNode(config);
        validateNodeConnectivity(config);
        validateNodeTypeCompatibility(config);
    }

    private void validateTriggerNode(WorkflowConfigDto config) {
        boolean hasTrigger = config.getNodes().stream()
                .anyMatch(n -> NodeType.TRIGGER.equals(n.getType()));

        if (!hasTrigger) {
            throw new IllegalArgumentException("Workflow must have at least one TRIGGER node");
        }
    }

    private void validateEndNode(WorkflowConfigDto config) {
        boolean hasEnd = config.getNodes().stream()
                .anyMatch(n -> NodeType.END.equals(n.getType()) || NodeType.ACTION.equals(n.getType()));

        if (!hasEnd) {
            throw new IllegalArgumentException("Workflow must have at least one END or ACTION node");
        }
    }

    private void validateNodeConnectivity(WorkflowConfigDto config) {
        Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
        config.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

        Set<String> reachableNodes = new HashSet<>();
        String triggerId = config.getNodes().stream()
                .filter(n -> NodeType.TRIGGER.equals(n.getType()))
                .findFirst()
                .map(WorkflowNodeDto::getId)
                .orElse(null);

        if (triggerId == null) {
            throw new IllegalArgumentException("No TRIGGER node found");
        }

        traverseGraph(triggerId, reachableNodes, config.getEdges(), nodeMap);

        for (WorkflowNodeDto node : config.getNodes()) {
            if (!reachableNodes.contains(node.getId()) && !NodeType.END.equals(node.getType())) {
                throw new IllegalArgumentException("Node '" + node.getId() + "' is not reachable from trigger");
            }
        }
    }

    private void traverseGraph(String nodeId, Set<String> visited, List<WorkflowEdgeDto> edges,
                               Map<String, WorkflowNodeDto> nodeMap) {
        if (visited.contains(nodeId)) {
            return;
        }

        visited.add(nodeId);

        for (WorkflowEdgeDto edge : edges) {
            if (edge.getSource().equals(nodeId) && nodeMap.containsKey(edge.getTarget())) {
                traverseGraph(edge.getTarget(), visited, edges, nodeMap);
            }
        }
    }

    private void validateNodeTypeCompatibility(WorkflowConfigDto config) {
        Map<String, WorkflowNodeDto> nodeMap = new HashMap<>();
        config.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

        for (WorkflowEdgeDto edge : config.getEdges()) {
            WorkflowNodeDto sourceNode = nodeMap.get(edge.getSource());
            WorkflowNodeDto targetNode = nodeMap.get(edge.getTarget());

            if (sourceNode == null || targetNode == null) {
                throw new IllegalArgumentException("Edge references non-existent node: " +
                        (sourceNode == null ? edge.getSource() : edge.getTarget()));
            }

            validateEdgeCompatibility(sourceNode, targetNode);
        }
    }

    private void validateEdgeCompatibility(WorkflowNodeDto source, WorkflowNodeDto target) {
        if (NodeType.END.equals(source.getType())) {
            throw new IllegalArgumentException("END nodes cannot have outgoing edges");
        }

        if (NodeType.TRIGGER.equals(source.getType())) {
            if (!NodeType.ACTION.equals(target.getType()) && !NodeType.CONDITION.equals(target.getType())) {
                throw new IllegalArgumentException("TRIGGER can only connect to ACTION or CONDITION nodes");
            }
        }

        if (NodeType.CONDITION.equals(source.getType())) {
            if (!NodeType.ACTION.equals(target.getType()) && !NodeType.END.equals(target.getType())) {
                throw new IllegalArgumentException("CONDITION can only connect to ACTION or END nodes");
            }
        }

        if (NodeType.ACTION.equals(source.getType())) {
            if (!NodeType.ACTION.equals(target.getType()) && !NodeType.CONDITION.equals(target.getType()) &&
                    !NodeType.END.equals(target.getType())) {
                throw new IllegalArgumentException("ACTION can only connect to ACTION, CONDITION, or END nodes");
            }
        }
    }
}
