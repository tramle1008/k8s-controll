package infra.k8s.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.dto.ClusterDto;
import infra.k8s.dto.cluster.ClusterImportRequest;
import infra.k8s.dto.cluster.ClusterManagementDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.User;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.AnsibleService;
import infra.k8s.service.ClusterControlService;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.iml.ClusterAccessService;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/clusters")
public class ClusterController {

    private  final ClusterRepository clusterRepository;
    private final ClusterManager clusterManager;
    private final AnsibleService ansibleService;
    private final ClusterControlService clusterControlService;
    private final ClusterAccessService clusterAccessService;

    @GetMapping
    public ResponseEntity<?> getClusters(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(clusterAccessService.getClustersForCurrentUser(user));
    }

    @GetMapping("/{clusterId}")
    public ResponseEntity<?> getClusterDetail(
            @PathVariable Long clusterId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(
                clusterAccessService.getClusterDetailForCurrentUser(user, clusterId)
        );
    }

    @PostMapping("/{id}/fetch-config")
    public ResponseEntity<String> testAddConfigClusterAvailable(@PathVariable Long id) {
        try {

            Cluster cluster = clusterRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Cluster không tồn tại: " + id));

            if (cluster.getStatus() != ClusterStatus.ACTIVE) {
                return ResponseEntity.badRequest()
                        .body("Cluster chưa ACTIVE, không thể lấy admin.conf");
            }

            ansibleService.fetchAndStoreAdminConf(id);

            return ResponseEntity.ok("Thêm file admin.conf thành công cho cluster: " + cluster.getName());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Thêm file thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/current")
    public ResponseEntity<ClusterDto> getCurrentCluster() {

        Long clusterId = clusterManager.getActiveClusterId();

        if (clusterId == null) {
            return ResponseEntity.noContent().build();
        }

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại"));

        ClusterDto dto = new ClusterDto(
                cluster.getId(),
                cluster.getName()
        );

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<Void> switchCluster(@PathVariable Long id) {
        clusterManager.setActiveCluster(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active")
    public ResponseEntity<List<ClusterDto>> getAllClusterActive() {
        return ResponseEntity.ok(clusterControlService.getAllClusterActive());
    }

    @GetMapping("/management")
    public ResponseEntity<List<ClusterManagementDto>> getAllClustersForManagement() {
        return ResponseEntity.ok(clusterControlService.getAllClustersForManagement());
    }

    @GetMapping("/management/{id}")
    public ResponseEntity<ClusterManagementDto> getClusterDetail(@PathVariable Long id) {
        return ResponseEntity.ok(clusterControlService.getClusterDetail(id));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClusterManagementDto> importCluster(
            @RequestPart("data") ClusterImportRequest request,
            @RequestPart("adminConfFile") MultipartFile adminConfFile
    ) {
        return ResponseEntity.ok(clusterControlService.importCluster(request, adminConfFile));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCluster(@PathVariable Long id) {
        clusterControlService.deleteCluster(id);
        return ResponseEntity.noContent().build();
    }

}
