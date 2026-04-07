package infra.k8s.dto.PV;
import lombok.Data;

import java.util.List;

@Data
public class PVRequest {
    private String name;
    private String capacity;
    private List<String> accessModes;
    private String reclaimPolicy;
    private String storageClassName;

    private String type; // hostPath, nfs, local

    private String hostPath;

    private String nfsServer;
    private String nfsPath;

    private String localPath;
}