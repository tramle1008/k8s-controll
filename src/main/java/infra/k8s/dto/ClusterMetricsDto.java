package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
public class ClusterMetricsDto {

    private double cpuPercent;
    private double cpuUsedCores;
    private int cpuTotalCores;

    private double memoryPercent;
    private double memoryUsedGi;
    private double memoryTotalGi;

    private int podsUsed;
    private int podsTotal;

    public ClusterMetricsDto(
            double cpuPercent,
            double cpuUsedCores,
            int cpuTotalCores,
            double memoryPercent,
            double memoryUsedGi,
            double memoryTotalGi,
            int podsUsed,
            int podsTotal
    ) {
        this.cpuPercent = cpuPercent;
        this.cpuUsedCores = cpuUsedCores;
        this.cpuTotalCores = cpuTotalCores;
        this.memoryPercent = memoryPercent;
        this.memoryUsedGi = memoryUsedGi;
        this.memoryTotalGi = memoryTotalGi;
        this.podsUsed = podsUsed;
        this.podsTotal = podsTotal;
    }
}