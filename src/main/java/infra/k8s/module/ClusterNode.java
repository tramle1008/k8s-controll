package infra.k8s.module;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import infra.k8s.Context.NodeRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Node.java toi doi thanh ClusterNode
@Entity
@Table(name = "nodes",
        uniqueConstraints = @UniqueConstraint(name = "uk_cluster_node", columnNames = {"cluster_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;
    @Column(length = 100, nullable = false)
    private String name;           // master-1, worker-3...
    @Column(length = 45)
    private String ipAddress;      // internal hoặc external IP
    @Enumerated(EnumType.STRING)
    private NodeRole role;         // MASTER, WORKER
    private String username = "ubuntu";  // hoặc user1, user2
    private Integer sshPort = 22;
    private Boolean ready = false;     // Trạng thái realtime từ K8s
    private Instant lastTransitionTime;
    private Instant updatedAt = Instant.now();
    private Boolean alerted = false;
    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeEvent> events = new ArrayList<>();
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}

