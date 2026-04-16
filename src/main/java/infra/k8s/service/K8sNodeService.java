package infra.k8s.service;

import infra.k8s.dto.ClusterMetricsDto;
import infra.k8s.dto.NodeSummary;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface K8sNodeService {
    List<NodeSummary> getAllNodes();
    ClusterMetricsDto getClusterMetrics();
    String drainNode(String nodeName);

    @Nullable String uncordonNode(String nodeName);
}