package infra.k8s.dto.container;

import lombok.Data;

@Data
public class EnvVarDto {
    private String name;
    private String value;
}