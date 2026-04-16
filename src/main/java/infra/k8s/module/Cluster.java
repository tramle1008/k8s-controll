package infra.k8s.module;


import jakarta.persistence.*;
import lombok.*;
import infra.k8s.Context.ClusterStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// Cluster.java
@Entity
@Table(name = "clusters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"clusterNodes", "users"})
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClusterStatus status = ClusterStatus.PENDING;

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClusterNode> clusterNodes = new ArrayList<>();

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<User> users = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String encryptedKubeconfig;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}

