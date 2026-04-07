package infra.k8s.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.module.Cluster;

import java.util.List;
import java.util.Optional;

public interface ClusterRepository extends JpaRepository<Cluster, Long> {
    Optional<Cluster> findByName(String name);

    List<Cluster> findByStatus(ClusterStatus clusterStatus);


    @EntityGraph(attributePaths = {"clusterNodes"})
    List<Cluster> findAll();

    @Query("""
SELECT c FROM Cluster c
LEFT JOIN FETCH c.clusterNodes
WHERE c.id = :id
""")
    Optional<Cluster> findClusterWithNodes(Long id);

    @Modifying
    @Query("UPDATE Cluster c SET c.status = :status WHERE c.id = :id")
    void updateStatus(Long id, ClusterStatus status);
}