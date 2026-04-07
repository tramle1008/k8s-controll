package infra.k8s.dto.node;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeMetricsDto {
    private String nodeName;
    private double cpuPercent;
    private double memoryPercent;
    private int podsUsed;
    private int podsCapacity;
}