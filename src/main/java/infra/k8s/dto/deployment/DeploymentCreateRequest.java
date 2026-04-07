package infra.k8s.dto.deployment;

import lombok.Data;
import infra.k8s.dto.common.MetadataDto;

@Data
public class DeploymentCreateRequest {
    private MetadataDto metadata;
    private DeploymentSpecDto spec;
}
