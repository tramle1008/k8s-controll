package infra.k8s.service;

import infra.k8s.dto.ClusterMetricsDto;
import infra.k8s.dto.NodeSummary;

import java.util.List;

public interface K8sNodeService {
    List<NodeSummary> getAllNodes();
    ClusterMetricsDto getClusterMetrics();




}