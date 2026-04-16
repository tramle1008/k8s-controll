package infra.k8s.dto;

import lombok.Data;
import infra.k8s.module.ClusterNode;

import java.time.Instant;

@Data
public class TopicNodeSummary {
    private Long id;
    private String name;
    private boolean ready;
    private String role;
    private String ipAddress;

    private Instant updatedAt;
    private boolean alerted;
    private Long clusterId;

    public TopicNodeSummary(ClusterNode node) {
        this.id = node.getId();
        this.name = node.getName();
        this.ready = Boolean.TRUE.equals(node.getReady());
        this.role = node.getRole() != null ? node.getRole().name() : null;
        this.ipAddress = node.getIpAddress();

        this.updatedAt = node.getUpdatedAt();
        this.alerted = Boolean.TRUE.equals(node.getAlerted());
        this.clusterId = node.getCluster() != null ? node.getCluster().getId() : null;
    }
}