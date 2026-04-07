package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentPodsDto {
    private String name;
    private String phase;
    private String nodeName;
    private Integer restartCount;
    private String podIP;
}
