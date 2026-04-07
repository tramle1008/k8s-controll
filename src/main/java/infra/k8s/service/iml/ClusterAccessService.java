package infra.k8s.service.iml;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import infra.k8s.Context.UserRole;
import infra.k8s.module.Cluster;
import infra.k8s.module.User;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClusterAccessService {

    private final ClusterRepository clusterRepository;
    private final UserRepository userRepository;

    public List<Cluster> getClustersForCurrentUser(User user) {
        if (user.getUserRole() == UserRole.ADMIN) {
            return clusterRepository.findAll();
        }

        if (user.getCluster() == null) {
            return List.of();
        }

        return List.of(user.getCluster());
    }

    public Cluster getClusterDetailForCurrentUser(User user, Long clusterId) {
        if (user.getUserRole() == UserRole.ADMIN) {
            return clusterRepository.findById(clusterId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cluster"));
        }

        if (user.getCluster() == null || !user.getCluster().getId().equals(clusterId)) {
            throw new RuntimeException("Bạn không có quyền truy cập cluster này");
        }

        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cluster"));
    }
}
