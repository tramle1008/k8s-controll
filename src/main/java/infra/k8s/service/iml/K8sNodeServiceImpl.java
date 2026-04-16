package infra.k8s.service.iml;

import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterNodeRepository;
import infra.k8s.repository.ClusterRepository;
import io.fabric8.kubernetes.api.model.*;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import io.fabric8.kubernetes.api.model.policy.v1.Eviction;
import io.fabric8.kubernetes.api.model.policy.v1.EvictionBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class K8sNodeServiceImpl implements K8sNodeService {


    private final ClusterManager clusterManager;
    private final ClusterNodeRepository clusterNodeRepository;
    private final Map<String, NodeMetricsDto> lastMetrics = new ConcurrentHashMap<>();
    @Override
    public List<NodeSummary> getAllNodes() {
        KubernetesClient client = clusterManager.requireActiveClient();
        List<ClusterNode> clusterNodes = clusterNodeRepository.findAll(); // lấy từ DB

        Map<String, ClusterNode> nodeMap = clusterNodes.stream()
                .collect(Collectors.toMap(
                        ClusterNode::getName,
                        Function.identity(),
                        (existing, replacement) -> existing.getUpdatedAt().isAfter(replacement.getUpdatedAt()) ? existing : replacement
                ));
        NodeList nodeList = client.nodes().list();

        return nodeList.getItems().stream()
                .map(k8sNode -> {
                    NodeSummary summary = toNodeSummary(k8sNode);

                    ClusterNode clusterNode = nodeMap.get(summary.getName());
                    if (clusterNode != null) {
                        summary.setUser(clusterNode.getUsername());
                        summary.setInternalIp(clusterNode.getIpAddress());
                        summary.setRole(clusterNode.getRole().name().toLowerCase());
                    }

                    return summary;
                })
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

    @Override
    public String drainNode(String nodeName) {
        KubernetesClient client = clusterManager.requireActiveClient();

        try {
            // ===== 1) Cordon node =====
            log.info("Cordoning node: {}", nodeName);
            client.nodes()
                    .withName(nodeName)
                    .edit(n -> new NodeBuilder(n)
                            .editOrNewSpec()
                            .withUnschedulable(true)
                            .endSpec()
                            .build()
                    );

            // ===== 2) Lấy tất cả pods trên node =====
            List<Pod> targetPods = client.pods().inAnyNamespace().list().getItems().stream()
                    .filter(p -> nodeName.equals(p.getSpec().getNodeName()))
                    // ignore mirror pods (kubectl drain luôn bỏ qua)
                    .filter(p -> p.getMetadata().getAnnotations() == null ||
                            !p.getMetadata().getAnnotations().containsKey("kubernetes.io/config.mirror"))
                    // ignore DaemonSet pods (do --ignore-daemonsets)
                    .filter(p -> p.getMetadata().getOwnerReferences() == null ||
                            p.getMetadata().getOwnerReferences().stream()
                                    .noneMatch(o -> "DaemonSet".equals(o.getKind())))
                    .collect(Collectors.toList());

            log.info("Pods to drain: {}", targetPods.size());

            // ===== 3) Evict từng pod =====
            for (Pod pod : targetPods) {
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();

                log.info("Evicting pod: {}/{}", ns, name);

                Eviction eviction = new EvictionBuilder()
                        .withNewMetadata()
                        .withName(name)
                        .withNamespace(ns)
                        .endMetadata()
                        .build();

                try {
                    client.pods()
                            .inNamespace(ns)
                            .withName(name)
                            .evict(eviction);

                } catch (Exception ex) {
                    // ==== Nếu gặp PDB (PodDisruptionBudget) → kubectl cũng retry ====
                    log.warn("Evict failed (retrying): {}/{}: {}", ns, name, ex.getMessage());

                    // fallback: delete pod theo kiểu --delete-emptydir-data
                    client.pods().inNamespace(ns).withName(name).delete();
                }

                // đợi pod biến mất giống kubectl
                waitForPodDeletion(client, ns, name);
            }

            return "Node drained thành công tại : " + nodeName;

        } catch (Exception e) {
            log.error("Drain node failed: {}", nodeName, e);
            throw new RuntimeException("Drain failed: " + e.getMessage());
        }
    }

    @Override
    public String uncordonNode(String nodeName) {
        KubernetesClient client = clusterManager.requireActiveClient();

        try {
            log.info("Uncordoning node: {}", nodeName);

            client.nodes()
                    .withName(nodeName)
                    .edit(n -> new NodeBuilder(n)
                            .editOrNewSpec()
                            .withUnschedulable(false)
                            .endSpec()
                            .build()
                    );

            return "Node uncordoned: " + nodeName;

        } catch (Exception e) {
            log.error("Failed to uncordon node {}", nodeName, e);
            throw new RuntimeException("Uncordon failed: " + e.getMessage());
        }
    }

    private void waitForPodDeletion(KubernetesClient client, String ns, String name) throws InterruptedException {
        int retry = 0;
        while (retry < 30) { // ~30s giống kubectl
            Pod p = client.pods().inNamespace(ns).withName(name).get();
            if (p == null) return;
            Thread.sleep(1000);
            retry++;
        }
        log.warn("Timeout waiting for pod {} in namespace {} to be deleted", name, ns);
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


}