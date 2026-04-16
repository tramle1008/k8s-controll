package infra.k8s.dto.container;

import lombok.Data;

@Data
public class EnvFromDto {
    private String type; // configMap | secret
    private String name;
}