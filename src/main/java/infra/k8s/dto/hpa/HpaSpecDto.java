package infra.k8s.dto.hpa;

import lombok.Data;

@Data
public class HpaSpecDto {

    private String targetKind;
    private String targetName;

    private Integer minReplicas;
    private Integer maxReplicas;

    private Integer cpuUtilization;

    private Integer memoryUtilization;

}