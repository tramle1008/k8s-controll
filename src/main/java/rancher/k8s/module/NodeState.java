package rancher.k8s.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeState {

    private String name;

    private boolean ready;

    private Instant lastTransitionTime;

    private Instant lastSeenTime;

    private boolean alerted;

    // Constructor tiện lợi khi tạo mới từ informer
    public NodeState(String name, boolean ready, Instant now) {
        this.name = name;
        this.ready = ready;
        this.lastTransitionTime = now;
        this.lastSeenTime = now;
        this.alerted = false;
    }
}