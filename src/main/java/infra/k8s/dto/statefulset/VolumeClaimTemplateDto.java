package infra.k8s.dto.statefulset;

import lombok.Data;

import java.util.List;

@Data
public class VolumeClaimTemplateDto {
    private String name;
    private String storageClassName;
    private List<String> accessModes;
    private String storage;
}