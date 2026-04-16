package infra.k8s.service;

import infra.k8s.dto.cluster.UserDto;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface UserService {
    List<UserDto> getAllUsers();

    void attachCluster(Long userId, Long clusterId);

    void deleteUser(Long userId);
}
