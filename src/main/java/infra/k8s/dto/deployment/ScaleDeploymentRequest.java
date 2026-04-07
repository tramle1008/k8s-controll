package infra.k8s.dto.deployment;

import lombok.Data;

@Data
public class ScaleDeploymentRequest {

    private Integer replicas;

}