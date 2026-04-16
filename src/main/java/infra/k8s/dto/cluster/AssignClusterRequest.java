package infra.k8s.dto.cluster;

import lombok.Data;

@Data
public class AssignClusterRequest {
    private String username;
    private Long clusterId; // Cluster muốn gán
}