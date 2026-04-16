package infra.k8s.dto.node;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class NodeMetricsDto {
    private String nodeName;
    private double cpuPercent;
    private double memoryPercent;
    private int podsUsed;
    private int podsCapacity;

    // mặc định = true
    private boolean metricsAvailable = true;

    // constructor cũ (để code bạn hiện tại không lỗi)
    public NodeMetricsDto(String nodeName,
                          double cpuPercent,
                          double memoryPercent,
                          int podsUsed,
                          int podsCapacity) {
        this.nodeName = nodeName;
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
        this.podsUsed = podsUsed;
        this.podsCapacity = podsCapacity;
    }

    // constructor mới nếu cần set metricsAvailable
    public NodeMetricsDto(String nodeName,
                          double cpuPercent,
                          double memoryPercent,
                          int podsUsed,
                          int podsCapacity,
                          boolean metricsAvailable) {
        this(nodeName, cpuPercent, memoryPercent, podsUsed, podsCapacity);
        this.metricsAvailable = metricsAvailable;
    }
}