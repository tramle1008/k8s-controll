package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.*;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import infra.k8s.dto.ClusterMetricsDto;
import infra.k8s.dto.NodeSummary;
import infra.k8s.dto.node.NodeMetricsDto;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.K8sNodeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class K8sNodeServiceImpl implements K8sNodeService {


    private final ClusterManager clusterManager;
    private final Map<String, NodeMetricsDto> lastMetrics = new ConcurrentHashMap<>();
    @Override
    public List<NodeSummary> getAllNodes() {
        KubernetesClient client = clusterManager.requireActiveClient();

        NodeList nodeList = client.nodes().list();

        return nodeList.getItems().stream()
                .map(this::toNodeSummary)
                .collect(Collectors.toList());
    }

    @Override
    public ClusterMetricsDto getClusterMetrics() {

        KubernetesClient client = clusterManager.requireActiveClient();

        NodeList nodeList = client.nodes().list();
        List<Node> nodes = nodeList.getItems();

        int totalCpu = 0;
        double totalMemoryGi = 0;
        int totalPods = 0;

        for (Node node : nodes) {

            String cpu = node.getStatus().getCapacity().get("cpu").getAmount();
            totalCpu += Integer.parseInt(cpu);

            var memQ = node.getStatus().getCapacity().get("memory");
            String memory = memQ.getAmount() + memQ.getFormat();
            totalMemoryGi += convertMemoryToGi(memory);

            // pod capacity
            String pods = node.getStatus().getCapacity().get("pods").getAmount();
            totalPods += Integer.parseInt(pods);
        }

        // metrics-server usage
        var metrics = client.top().nodes().metrics();

        long cpuUsageMilli = 0;
        long memoryUsageMi = 0;

        for (var m : metrics.getItems()) {

            var cpuQ = m.getUsage().get("cpu");
            String cpu = cpuQ.getAmount() + cpuQ.getFormat();
            cpuUsageMilli += parseCpu(cpu);

            var memQ = m.getUsage().get("memory");
            String memory = memQ.getAmount() + memQ.getFormat();
            memoryUsageMi += parseMemory(memory);
        }

        double cpuUsedCore = cpuUsageMilli / 1000.0;
        double cpuPercent = (cpuUsedCore / totalCpu) * 100;

        double memoryUsedGi = memoryUsageMi / 1024.0;
        double memoryPercent = (memoryUsedGi / totalMemoryGi) * 100;

        // pods used
        int podsUsed = client.pods().inAnyNamespace().list().getItems().size();

        return new ClusterMetricsDto(
                Math.round(cpuPercent),
                Math.round(cpuUsedCore * 100.0) / 100.0,
                totalCpu,
                Math.round(memoryPercent),
                Math.round(memoryUsedGi * 100.0) / 100.0,
                Math.round(totalMemoryGi * 100.0) / 100.0,
                podsUsed,
                totalPods
        );
    }


    private long parseCpu(String cpu) {

        if (cpu.endsWith("n")) {
            return Long.parseLong(cpu.replace("n", "")) / 1000000;
        }

        if (cpu.endsWith("m")) {
            return Long.parseLong(cpu.replace("m", ""));
        }

        return Long.parseLong(cpu) * 1000;
    }

    private long parseMemory(String memory) {

        if (memory.endsWith("Ki")) {
            return Long.parseLong(memory.replace("Ki", "")) / 1024;
        }

        if (memory.endsWith("Mi")) {
            return Long.parseLong(memory.replace("Mi", ""));
        }

        if (memory.endsWith("Gi")) {
            return Long.parseLong(memory.replace("Gi", "")) * 1024;
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

    private NodeSummary toNodeSummary(Node node) {
        NodeStatus status = node.getStatus();
        NodeSystemInfo info = status.getNodeInfo();
//        System.out.println(status.getCapacity().get("memory"));
        return NodeSummary.builder()
                .name(node.getMetadata().getName())
                .status(getReadyStatus(status))
                .role(determineRole(node.getMetadata().getLabels()))
                .age(formatAge(node.getMetadata().getCreationTimestamp()))
                .version(info != null ? info.getKubeletVersion() : "N/A")
                .internalIp(getInternalIp(status))
                .cpuCapacity(getQuantity(status.getCapacity(), "cpu"))
                .cpuAllocatable(getQuantity(status.getAllocatable(), "cpu"))
                .memoryCapacity(formatMemory(status.getCapacity(), "memory"))
                .memoryAllocatable(formatMemory(status.getAllocatable(), "memory"))
                .podsAllocatable(getQuantity(status.getAllocatable(), "pods"))
                .taints(getTaints(node))
                .conditionsSummary(getConditionsSummary(status))
                .build();
    }

    public String getNodesRaw() {
        KubernetesClient client = clusterManager.requireActiveClient();

        return client.nodes().list().toString();
    }

    private String getReadyStatus(NodeStatus status) {
        return status.getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .findFirst()
                .map(NodeCondition::getStatus)
                .orElse("Unknown");
    }

    private String determineRole(java.util.Map<String, String> labels) {
        if (labels.containsKey("node-role.kubernetes.io/control-plane") ||
                labels.containsKey("node-role.kubernetes.io/master")) {
            return "control-plane";
        }
        return "worker";
    }

    private String formatAge(String creationTimestamp) {
        if (creationTimestamp == null) return "N/A";
        Instant created = Instant.parse(creationTimestamp);
        Duration duration = Duration.between(created, Instant.now());
        long days = duration.toDays();
        if (days > 0) return days + "d";
        long hours = duration.toHours() % 24;
        if (hours > 0) return hours + "h";
        return duration.toMinutes() % 60 + "m";
    }

    private String getInternalIp(NodeStatus status) {
        return status.getAddresses().stream()
                .filter(a -> "InternalIP".equals(a.getType()))
                .map(NodeAddress::getAddress)
                .findFirst()
                .orElse("N/A");
    }

    private String getQuantity(java.util.Map<String, Quantity> map, String key) {
        Quantity q = map.get(key);
        return q != null ? q.getAmount() : "0";
    }

    private String formatMemory(Map<String, Quantity> map, String key) {
        Quantity q = map.get(key);
        if (q == null) return "0";

        BigDecimal bytes = Quantity.getAmountInBytes(q);

        BigDecimal gi = bytes.divide(
                BigDecimal.valueOf(1024 * 1024 * 1024),
                2,
                RoundingMode.HALF_UP
        );

        return gi + " Gi";
    }
    private List<String> getTaints(Node node) {
        if (node.getSpec().getTaints() == null) return List.of();
        return node.getSpec().getTaints().stream()
                .map(t -> t.getKey() + (t.getValue() != null ? "=" + t.getValue() : "") + ":" + t.getEffect())
                .toList();
    }

    private String getConditionsSummary(NodeStatus status) {
        return status.getConditions().stream()
                .filter(c -> "True".equals(c.getStatus()) || "Ready".equals(c.getType()))
                .map(c -> c.getType() + (c.getStatus().equals("True") ? "" : "=" + c.getStatus()))
                .collect(Collectors.joining(", "));
    }

    public Collection<NodeMetricsDto> getLastMetrics() {

        KubernetesClient client = clusterManager.getActiveClient();

        if (client == null) {
            return lastMetrics.values();
        }

        var nodes = client.nodes().list().getItems();

        List<NodeMetricsDto> result = new ArrayList<>();

        for (Node node : nodes) {

            String nodeName = node.getMetadata().getName();

            NodeMetricsDto m = lastMetrics.get(nodeName);

            if (m != null) {
                result.add(m);
            } else {

                // fallback nếu chưa có metrics
                result.add(new NodeMetricsDto(
                        nodeName,
                        0,
                        0,
                        0,
                        Integer.parseInt(
                                node.getStatus().getCapacity().get("pods").getAmount()
                        )
                ));

            }
        }

        return result;
    }
}