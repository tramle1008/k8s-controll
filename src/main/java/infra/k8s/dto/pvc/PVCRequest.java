package infra.k8s.dto.pvc;

import lombok.Data;

import java.util.List;

@Data
public class PVCRequest {
    private String name;
    private String namespace;
    private List<String> accessModes;
    private String storage;
    private String storageClassName;
    private String volumeName;
}