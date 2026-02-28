package rancher.k8s.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClusterRequest {
    private String clusterName;
    private List<Node> masterNodes;
    private List<Node> workerNodes;
    private String metallbRange;

    @Data
    public static class Node {
        private String hostname;
        private String ip;
    }
}