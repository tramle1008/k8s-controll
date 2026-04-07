package infra.k8s.dto.statefulset;

import lombok.Data;

@Data
public class StatefulSetDto {
    private String name;
    private String namespace;
    private Integer replicas;
    private Integer readyReplicas;
    private String serviceName;
    private String creationTimestamp;
}