package io.o2m.core.util;

import io.o2m.model.TableMetadata;

import java.util.*;

public final class DependencySortUtil {
    private DependencySortUtil() {}

    /**
     * Sort tables by foreign key dependencies using Kahn's algorithm.
     * Tables that are referenced by FK must appear before the tables that reference them.
     * Circular dependencies are appended at the end in their original order.
     */
    public static List<TableMetadata> sortTables(List<TableMetadata> tables) {
        Map<String, TableMetadata> tableMap = new LinkedHashMap<>();
        for (TableMetadata t : tables) {
            tableMap.put(t.name().toUpperCase(), t);
        }

        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (TableMetadata t : tables) {
            String name = t.name().toUpperCase();
            graph.putIfAbsent(name, new HashSet<>());
            inDegree.putIfAbsent(name, 0);
        }

        for (TableMetadata t : tables) {
            String name = t.name().toUpperCase();
            for (var fk : t.foreignKeys()) {
                if (fk.deferred()) continue;
                String ref = fk.referencedTable().toUpperCase();
                if (tableMap.containsKey(ref) && !ref.equals(name)) {
                    graph.putIfAbsent(ref, new HashSet<>());
                    if (graph.get(ref).add(name)) {
                        inDegree.put(name, inDegree.getOrDefault(name, 0) + 1);
                    }
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (String name : tableMap.keySet()) {
            if (inDegree.getOrDefault(name, 0) == 0) {
                queue.add(name);
            }
        }

        List<TableMetadata> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String name = queue.poll();
            sorted.add(tableMap.get(name));
            for (String dependent : graph.getOrDefault(name, Set.of())) {
                int degree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, degree);
                if (degree == 0) {
                    queue.add(dependent);
                }
            }
        }

        for (TableMetadata t : tables) {
            if (!sorted.contains(t)) {
                sorted.add(t);
            }
        }

        return sorted;
    }
}
