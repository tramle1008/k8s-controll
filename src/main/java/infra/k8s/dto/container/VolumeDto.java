package infra.k8s.dto.container;

import lombok.Data;

@Data
public class VolumeDto {
    private String name;
    private String type; // configMap | secret | emptyDir | pvc
    private String configMapName;
    private String secretName;
    private String pvcName;
}