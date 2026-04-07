package infra.k8s.dto.PV;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PVResponse {
    private String name;
    private String capacity;
    private String accessModes;
    private String reclaimPolicy;
    private String status;
    private String storageClass;
    private String claim;
    private String age;
}