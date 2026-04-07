package infra.k8s.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import infra.k8s.Context.NodeRole;
import infra.k8s.module.ClusterNode;

import java.util.List;
import java.util.Optional;

public interface ClusterNodeRepository extends JpaRepository<ClusterNode, Long> {
    // Query tìm node theo clusterId và name (đã có)
    Optional<ClusterNode> findByClusterIdAndName(Long clusterId, String name);
    void deleteByClusterIdAndName(Long clusterId, String name);

    // Query tìm tất cả node Not Ready (không cần clusterId nếu chỉ 1 cluster)
    @Query("SELECT n FROM ClusterNode n WHERE n.ready = false")
    List<ClusterNode> findNotReadyNodes();

    // Nếu muốn theo cluster cụ thể (dù hiện tại chỉ 1 cluster)
    @Query("SELECT n FROM ClusterNode n WHERE n.ready = false AND n.cluster.id = :clusterId")
    List<ClusterNode> findNotReadyNodesByClusterId(@Param("clusterId") Long clusterId);
    long countByClusterIdAndRole(Long clusterId, NodeRole role);
    Optional<ClusterNode> findFirstByClusterIdAndRole(Long clusterId, NodeRole role);
}