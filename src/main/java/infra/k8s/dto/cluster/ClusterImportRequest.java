package infra.k8s.dto.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import infra.k8s.Context.NodeRole;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterImportRequest {
    private String clusterName;
    private List<NodeImportRequest> nodes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeImportRequest {
        private String name;
        private String ipAddress;
        private NodeRole role;
        private String username;
    }
}