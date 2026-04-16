package infra.k8s.dto.container;

import lombok.Data;

@Data
public class VolumeMountDto {
    private String name;
    private String mountPath;
    private Boolean readOnly;
    private String subPath;
}