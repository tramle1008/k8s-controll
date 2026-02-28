package rancher.k8s.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class PodSummaryDto {
    private long totalPods;
    private long runningPods;
    private long pendingPods;
    private long failedPods;
    private long succeededPods;
    private long unknownPods;

    private long alertedPods;      // số pod đang alerted (down lâu)
    private String lastUpdated;    // ISO string

    // Constructor tiện lợi
    public PodSummaryDto() {
        this.lastUpdated = java.time.Instant.now().toString();
    }
}