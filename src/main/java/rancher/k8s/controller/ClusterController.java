package rancher.k8s.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rancher.k8s.dto.ClusterRequest;
import rancher.k8s.service.ClusterService;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    @PostMapping("/create-cluster")
    public ResponseEntity<String> createCluster(@RequestBody ClusterRequest request) {
        try {
            String result = clusterService.createCluster(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Lỗi tạo cluster", e);
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/test-ssh")
    public ResponseEntity<String> testSsh() {
        try {
            String result = clusterService.testSshConnection();
            return ResponseEntity.ok("SSH test thành công: " + result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi SSH: " + e.getMessage());
        }
    }
}