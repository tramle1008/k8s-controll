package rancher.k8s.service.iml;

import io.fabric8.kubernetes.api.model.*;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rancher.k8s.dto.NodeSummary;
import rancher.k8s.service.K8sNodeService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class K8sNodeServiceImpl implements K8sNodeService {

    private final KubernetesClient kubernetesClient;

    @Override
    public List<NodeSummary> getAllNodes() {
        NodeList nodeList = kubernetesClient.nodes().list();

        return nodeList.getItems().stream()
                .map(this::toNodeSummary)
                .collect(Collectors.toList());
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
        return kubernetesClient.nodes().list().toString();
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