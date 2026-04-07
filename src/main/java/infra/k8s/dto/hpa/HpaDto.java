package infra.k8s.dto.hpa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HpaDto {
    private String name;
    private String namespace;
    private String reference; // Deployment/xyz
    private String target; // cpu: 0%/70%
    private Integer minPods;
    private Integer maxPods;
    private String replicas;
    private String age;
}