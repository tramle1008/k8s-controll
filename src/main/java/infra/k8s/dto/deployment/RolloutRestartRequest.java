package infra.k8s.dto.deployment;

import lombok.Data;

@Data
public class RolloutRestartRequest {
    private String namespace;
    private String deploymentName;
}