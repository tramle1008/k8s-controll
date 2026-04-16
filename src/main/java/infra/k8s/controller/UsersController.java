package infra.k8s.controller;

import infra.k8s.dto.cluster.UserDto;
import infra.k8s.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // 2. Gán cluster cho user
    @PutMapping("/{userId}/attach-cluster/{clusterId}")
    public ResponseEntity<String> attachCluster(
            @PathVariable Long userId,
            @PathVariable Long clusterId
    ) {
        userService.attachCluster(userId, clusterId);
        return ResponseEntity.ok("Gán cluster cho user thành công");
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

}
