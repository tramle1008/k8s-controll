package rancher.k8s.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rancher.k8s.module.NodeEvent;

import java.time.Instant;

public interface NodeEventRepository
        extends JpaRepository<NodeEvent, Long> {

    void deleteByCreatedAtBefore(Instant time);
}
