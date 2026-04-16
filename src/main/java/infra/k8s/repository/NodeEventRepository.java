package infra.k8s.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import infra.k8s.module.NodeEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NodeEventRepository
        extends JpaRepository<NodeEvent, Long> {
    List<NodeEvent> findByNodeIdOrderByCreatedAtDesc(Long nodeId);

    // Xóa events cũ (dùng trong scheduled job)
    void deleteByCreatedAtBefore(Instant before);
    @Modifying
    @Query("DELETE FROM NodeEvent e WHERE e.node.id = :nodeId")
    void deleteByNodeId(@Param("nodeId") Long nodeId);
}
