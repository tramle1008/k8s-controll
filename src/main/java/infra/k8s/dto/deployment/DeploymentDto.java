package infra.k8s.dto.deployment;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import lombok.Data;
//DTO trả về danh sách Deployment

@Data
public class DeploymentDto {
    private String name;
    private String namespace;
    private Integer replicas;
    private Integer availableReplicas;  // Giữ Integer để có thể null nếu cần, nhưng ta sẽ set default
    private String status;

    public DeploymentDto(Deployment deployment) {
        this.name = deployment.getMetadata().getName();
        this.namespace = deployment.getMetadata().getNamespace();

        // Replicas: fallback về 0 nếu null (hiếm xảy ra)
        this.replicas = deployment.getSpec() != null && deployment.getSpec().getReplicas() != null
                ? deployment.getSpec().getReplicas()
                : 0;

        // availableReplicas: nếu null thì set về 0 (đây là fix chính)
        this.availableReplicas = deployment.getStatus() != null
                ? (deployment.getStatus().getAvailableReplicas() != null
                ? deployment.getStatus().getAvailableReplicas()
                : 0)
                : 0;

        // Status: ưu tiên condition "Available"
        this.status = "Unknown";
        if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
            for (DeploymentCondition condition : deployment.getStatus().getConditions()) {
                if ("Available".equals(condition.getType())) {
                    this.status = condition.getStatus();  // "True", "False", "Unknown"
                    break;
                }
            }
        }

        // Optional: Nếu replicas == 0, override status cho rõ ràng (tùy bạn)
        if (this.replicas == 0) {
            this.status = "ScaledDown";  // Hoặc "Ready" / "True" nếu muốn giữ nguyên
        }
    }
}