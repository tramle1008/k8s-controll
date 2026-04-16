package infra.k8s.dto.statefulset;

import lombok.Data;

@Data
public class StatefulSetScaleRequest {
    private int replicas;
}