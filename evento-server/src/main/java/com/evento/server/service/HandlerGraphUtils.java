package com.evento.server.service;

import com.evento.server.domain.model.core.Handler;

import java.util.*;
import java.util.stream.Collectors;

public class HandlerGraphUtils {

    public static List<Handler> getTopologicalOrder(List<Handler> handlers) {
        // Map handler UUID -> handler
        Map<String, Handler> handlerMap = handlers.stream()
                .collect(Collectors.toMap(Handler::getUuid, h -> h));

        // Build graph edges
        Map<String, List<String>> graph = new HashMap<>();
        for (Handler h : handlers) {
            graph.putIfAbsent(h.getUuid(), new ArrayList<>());
        }

        for (Handler source : handlers) {
            for (Handler target : handlers) {
                if (source == target) continue;

                // Case 1: returnType -> handledPayload
                if (source.getReturnType() != null &&
                        source.getReturnType().equals(target.getHandledPayload())) {
                    graph.get(source.getUuid()).add(target.getUuid());
                }

                // Case 2: invocations -> handledPayload
                if (source.getInvocations() != null) {
                    for (var invoked : source.getInvocations().values()) {
                        if (invoked.equals(target.getHandledPayload())) {
                            graph.get(source.getUuid()).add(target.getUuid());
                        }
                    }
                }
            }
        }

        // Detect cycle and topo sort with DFS
        List<String> topoOrder = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        List<String> cycle = new ArrayList<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                if (dfs(node, graph, visited, stack, topoOrder, cycle)) {
                    throw new RuntimeException("Cycle detected: \n" + handlers.stream().filter(h -> cycle.contains(h.getUuid())).map(h -> h.getComponent().getComponentName() + "(" + h.getHandledPayload().getName() + "): " + (h.getReturnType() == null ? "void" : h.getReturnType().getName()) +
                            h.getInvocations().values().stream().map(p -> "\n -> " + p.getName()).collect(Collectors.joining())).collect(Collectors.joining("\n||\n")));
                }
            }
        }

        // Reverse postorder to get topological order
        Collections.reverse(topoOrder);
        return topoOrder.stream().map(handlerMap::get).collect(Collectors.toList());
    }

    private static boolean dfs(String node,
                               Map<String, List<String>> graph,
                               Set<String> visited,
                               Set<String> stack,
                               List<String> topoOrder,
                               List<String> cycle) {
        visited.add(node);
        stack.add(node);

        for (String neighbor : graph.get(node)) {
            if (!visited.contains(neighbor)) {
                if (dfs(neighbor, graph, visited, stack, topoOrder, cycle)) {
                    if (cycle.isEmpty() || !cycle.getFirst().equals(neighbor)) {
                        cycle.add(node); // reconstruct cycle path
                    }
                    return true;
                }
            } else if (stack.contains(neighbor)) {
                // Found back edge → cycle
                cycle.add(neighbor);
                cycle.add(node);
                return true;
            }
        }

        stack.remove(node);
        topoOrder.add(node);
        return false;
    }
}
