package rancher.k8s.dto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class NodeSummary {
    private String name;
    private String status;              // Ready / NotReady / Unknown
    private String role;                // control-plane, worker, ...
    private String age;                 // 5d, 23h, ...
    private String version;
    private String internalIp;
    private String cpuCapacity;
    private String cpuAllocatable;
    private String memoryCapacity;
    private String memoryAllocatable;
    private String podsAllocatable;
    private List<String> taints;
    private String conditionsSummary;
}