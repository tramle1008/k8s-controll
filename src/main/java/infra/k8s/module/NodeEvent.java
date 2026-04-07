package infra.k8s.module;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "node_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)  // nullable = false để bắt buộc có node
    private ClusterNode node;  // Phải là ClusterNode (khớp với entity bạn đổi tên)
    private String oldStatus;
    private String newStatus;
    private Instant createdAt = Instant.now();
}