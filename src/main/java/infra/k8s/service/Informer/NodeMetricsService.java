package infra.k8s.service.Informer;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import infra.k8s.dto.node.NodeMetricsDto;
import infra.k8s.service.ClusterManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeMetricsService {

    private final ClusterManager clusterManager;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, NodeMetricsDto> lastMetrics = new ConcurrentHashMap<>();

    public List<NodeMetricsDto> collectCurrentMetrics() {

        KubernetesClient client = clusterManager.getActiveClient();
        if (client == null) return List.of();

        var metrics = client.top().nodes().metrics();
        var nodes = client.nodes().list().getItems();
        var pods = client.pods().inAnyNamespace().list().getItems();

        Map<String, Node> nodeMap = new HashMap<>();
        for (Node n : nodes) {
            nodeMap.put(n.getMetadata().getName(), n);
        }

        Map<String, Integer> podsPerNode = new HashMap<>();

        for (var pod : pods) {
            String nodeName = pod.getSpec().getNodeName();
            if (nodeName == null) continue;
            podsPerNode.merge(nodeName, 1, Integer::sum);
        }

        List<NodeMetricsDto> result = new ArrayList<>();

        for (NodeMetrics m : metrics.getItems()) {

            String nodeName = m.getMetadata().getName();
            Node node = nodeMap.get(nodeName);

            if (node == null) continue;

            double cpuPercent = calculateCpuPercent(node, m);
            double memPercent = calculateMemoryPercent(node, m);

            int podsCapacity = Integer.parseInt(
                    node.getStatus().getCapacity().get("pods").getAmount()
            );

            int podsUsed = podsPerNode.getOrDefault(nodeName, 0);

            result.add(new NodeMetricsDto(
                    nodeName,
                    round(cpuPercent),
                    round(memPercent),
                    podsUsed,
                    podsCapacity
            ));
        }

        return result;
    }

    @Scheduled(fixedRate = 10000)
    public void collectNodeMetrics() {
        KubernetesClient client = clusterManager.getActiveClient();
        if (client == null) {
            log.warn("No active cluster → skip metrics collection");
            return;
        }
        try {

            var metrics = client.top().nodes().metrics();
            var nodeList = client.nodes().list().getItems();

            // build map để lookup nhanh
            Map<String, Node> nodeMap = new HashMap<>();
            for (Node n : nodeList) {
                nodeMap.put(n.getMetadata().getName(), n);
            }

            // load tất cả pods 1 lần (tránh gọi API nhiều lần)
            var pods = client.pods().inAnyNamespace().list().getItems();

            Map<String, Integer> podsPerNode = new HashMap<>();
            for (var pod : pods) {

                String nodeName = pod.getSpec().getNodeName();
                if (nodeName == null) continue;

                podsPerNode.merge(nodeName, 1, Integer::sum);
            }

            List<NodeMetricsDto> changed = new ArrayList<>();

            for (NodeMetrics m : metrics.getItems()) {

                String nodeName = m.getMetadata().getName();
                Node node = nodeMap.get(nodeName);

                if (node == null) continue;

                double cpuPercent = calculateCpuPercent(node, m);
                double memoryPercent = calculateMemoryPercent(node, m);

                int podsCapacity = Integer.parseInt(
                        node.getStatus().getCapacity().get("pods").getAmount()
                );

                int podsUsed = podsPerNode.getOrDefault(nodeName, 0);

                NodeMetricsDto newMetric = new NodeMetricsDto(
                        nodeName,
                        round(cpuPercent),
                        round(memoryPercent),
                        podsUsed,
                        podsCapacity
                );

                NodeMetricsDto oldMetric = lastMetrics.get(nodeName);

                if (oldMetric == null || hasSignificantChange(oldMetric, newMetric)) {

                    changed.add(newMetric);
                    lastMetrics.put(nodeName, newMetric);
                }
            }

            if (!changed.isEmpty()) {

                messagingTemplate.convertAndSend(
                        "/topic/node-metrics",
                        changed
                );

                log.debug("Pushed {} node metrics updates", changed.size());
            }

        } catch (Exception e) {

            log.warn("Metrics API not available: {}", e.getMessage());
        }
    }

    private boolean hasSignificantChange(NodeMetricsDto oldM,
                                         NodeMetricsDto newM) {

        double cpuDiff = Math.abs(newM.getCpuPercent() - oldM.getCpuPercent());
        double memDiff = Math.abs(newM.getMemoryPercent() - oldM.getMemoryPercent());

        return cpuDiff > 2 || memDiff > 2;
    }

    private double calculateCpuPercent(Node node, NodeMetrics m) {

        Quantity cpuQ = m.getUsage().get("cpu");
        if (cpuQ == null) return 0;

        long cpuMilli = parseCpu(cpuQ);

        int cpuTotal = Integer.parseInt(
                node.getStatus().getCapacity().get("cpu").getAmount()
        );

        double cpuUsedCore = cpuMilli / 1000.0;

        return (cpuUsedCore / cpuTotal) * 100;
    }

    private double calculateMemoryPercent(Node node, NodeMetrics m) {

        Quantity memQ = m.getUsage().get("memory");
        if (memQ == null) return 0;

        long memoryMi = parseMemory(memQ);

        Quantity capacity = node.getStatus().getCapacity().get("memory");

        double totalGi = convertMemoryToGi(
                capacity.getAmount() + capacity.getFormat()
        );

        double usedGi = memoryMi / 1024.0;

        return (usedGi / totalGi) * 100;
    }

    private long parseCpu(Quantity cpu) {

        String val = cpu.getAmount() + cpu.getFormat();

        if (val.endsWith("n")) {
            return Long.parseLong(val.replace("n", "")) / 1_000_000;
        }

        if (val.endsWith("m")) {
            return Long.parseLong(val.replace("m", ""));
        }

        return Long.parseLong(val) * 1000;
    }

    private long parseMemory(Quantity memory) {

        String val = memory.getAmount() + memory.getFormat();

        if (val.endsWith("Ki")) {
            return Long.parseLong(val.replace("Ki", "")) / 1024;
        }

        if (val.endsWith("Mi")) {
            return Long.parseLong(val.replace("Mi", ""));
        }

        if (val.endsWith("Gi")) {
            return Long.parseLong(val.replace("Gi", "")) * 1024;
        }

        return 0;
    }

    private double convertMemoryToGi(String memory) {

        if (memory.endsWith("Ki")) {
            double ki = Double.parseDouble(memory.replace("Ki", ""));
            return ki / 1024 / 1024;
        }

        if (memory.endsWith("Mi")) {
            double mi = Double.parseDouble(memory.replace("Mi", ""));
            return mi / 1024;
        }

        if (memory.endsWith("Gi")) {
            return Double.parseDouble(memory.replace("Gi", ""));
        }

        return 0;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}