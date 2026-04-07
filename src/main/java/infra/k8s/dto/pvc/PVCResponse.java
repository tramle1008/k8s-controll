package infra.k8s.dto.pvc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PVCResponse {
    private String name;
    private String namespace;
    private String status;
    private String volume;
    private String capacity;
    private String accessModes;
    private String storageClass;
    private String age;
}