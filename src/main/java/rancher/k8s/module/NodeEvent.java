package rancher.k8s.module;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "node_event",
        indexes = {
                @Index(name = "idx_node_name", columnList = "nodeName"),
                @Index(name = "idx_created_at", columnList = "createdAt")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String nodeName;

    private String oldStatus;
    private String newStatus;
    private Instant createdAt;
}