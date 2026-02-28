package rancher.k8s.module;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "node_status")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class NodeStatus {

    @Id
    @Column(length = 100)
    private String name;

    @Column(nullable = false)
    private boolean ready;

    private String role;

    @Column(name = "internal_ip")
    private String internalIp;

    @Column(name = "last_transition_time")
    private Instant lastTransitionTime;

    @Column(name = "updated_at")
    private Instant updatedAt;
}