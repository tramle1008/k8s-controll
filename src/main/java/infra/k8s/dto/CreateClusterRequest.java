package infra.k8s.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateClusterRequest {
    private String clusterName;
    private List<Node> masterNodes;
    private List<Node> workerNodes;

    @Data
    public static class Node {
        private String hostname;
        private String ip;
    }
}