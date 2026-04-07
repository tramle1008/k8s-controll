package infra.k8s.dto;

public class ClusterCreateResponse {

    private Long clusterId;
    private String message;

    public ClusterCreateResponse(Long clusterId, String message) {
        this.clusterId = clusterId;
        this.message = message;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public String getMessage() {
        return message;
    }
}