package rancher.k8s.service;

import org.springframework.stereotype.Service;
import rancher.k8s.dto.NodeSummary;

import java.util.List;

public interface K8sNodeService {
    List<NodeSummary> getAllNodes();

}