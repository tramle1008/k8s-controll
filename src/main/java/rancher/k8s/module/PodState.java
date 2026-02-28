package rancher.k8s.module;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PodState {
    private String name;
    private String namespace;
    private String phase;              // Running, Pending, Failed,...
    private boolean ready;             // condition Ready = True
    private Instant lastTransitionTime;
    private Instant lastSeenTime;
    private boolean alerted;

    public PodState(String name, String namespace, String phase, boolean ready, Instant now) {
        this.name = name;
        this.namespace = namespace;
        this.phase = phase;
        this.ready = ready;
        this.lastTransitionTime = now;
        this.lastSeenTime = now;
        this.alerted = false;
    }
}