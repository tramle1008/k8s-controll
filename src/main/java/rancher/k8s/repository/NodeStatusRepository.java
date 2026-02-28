package rancher.k8s.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rancher.k8s.module.NodeStatus;

public interface NodeStatusRepository
        extends JpaRepository<NodeStatus, String> {
}