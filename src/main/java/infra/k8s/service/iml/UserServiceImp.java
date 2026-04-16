package infra.k8s.service.iml;

import infra.k8s.dto.cluster.UserDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.User;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.repository.UserRepository;
import infra.k8s.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImp implements UserService {
    private final UserRepository userRepository;
    private final ClusterRepository clusterRepository;

    // 1. Lấy danh sách user + cluster (cluster có thể null)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserDto(
                        user.getId(),
                        user.getUsername(),
                        user.getUserRole(),
                        user.getCluster() != null ? user.getCluster().getName() : null
                ))
                .toList();
    }
    // 2. Gán cluster còn trống cho user
    public void attachCluster(Long userId, Long clusterId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại"));

        // Nếu user khác đang sở hữu cluster này -> không cho gán
        if (!cluster.getUsers().isEmpty()) {
            throw new RuntimeException("Cluster này đã có user sở hữu");
        }

        user.setCluster(cluster);
        userRepository.save(user);
    }

    // 3. Xóa user
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));
        userRepository.delete(user);
    }

}
