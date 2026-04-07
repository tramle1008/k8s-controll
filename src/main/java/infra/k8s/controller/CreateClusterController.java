package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import infra.k8s.dto.ClusterCreateResponse;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.Context.NodeRole;
import infra.k8s.dto.ClusterCreationRequest;
import infra.k8s.dto.NodeDto;
import infra.k8s.dto.SshDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterNodeRepository;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.AnsibleService;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.SshKeyService;
import infra.k8s.service.iml.ClusterDeployService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/create")
@RequiredArgsConstructor
@Slf4j
public class CreateClusterController {

    private final SshKeyService sshKeyService;
    private final ClusterRepository clusterRepository;
    private final ClusterNodeRepository clusterNodeRepository;
    private final AnsibleService ansibleService;
    private final ClusterManager clusterManager;
    private final ClusterDeployService clusterDeployService;

    @PostMapping("/cluster")
    public ResponseEntity<?> createCluster(@RequestBody ClusterCreationRequest request) {

        try {

            // =============================
            // 1. Validation
            // =============================

            if (request.getClusterName() == null || request.getClusterName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Tên cluster không được để trống");
            }

            if (clusterRepository.findByName(request.getClusterName()).isPresent()) {
                return ResponseEntity.badRequest().body("Tên cluster đã tồn tại");
            }

            if (request.getNodes() == null || request.getNodes().isEmpty()) {
                return ResponseEntity.badRequest().body("Cần ít nhất một node");
            }

            // =============================
            // 2. Tạo Cluster object + validate node + gom password từng node
            // =============================



            Cluster cluster = new Cluster();
            cluster.setName(request.getClusterName().trim());

            cluster.setStatus(ClusterStatus.PENDING);

            List<ClusterNode> nodes = new ArrayList<>();
            Map<String, String> nodePasswords = new HashMap<>();

            for (NodeDto dto : request.getNodes()) {


                if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Node name không được để trống");
                }

                if (dto.getIp() == null || dto.getIp().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Node " + dto.getName() + " chưa có IP");
                }

                if (dto.getRole() == null || dto.getRole().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Node " + dto.getName() + " chưa có role");
                }

                if (dto.getSsh() == null) {
                    return ResponseEntity.badRequest().body("Node " + dto.getName() + " chưa có cấu hình SSH");
                }

                SshDto ssh = dto.getSsh();

                if (!"password".equalsIgnoreCase(ssh.getMethod())) {
                    return ResponseEntity.badRequest()
                            .body("Hiện tại chỉ hỗ trợ SSH method = password cho node " + dto.getName());
                }

                if (ssh.getPassword() == null || ssh.getPassword().trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body("Node " + dto.getName() + " chưa có SSH password");
                }

                Integer sshPort = (ssh.getSshPort() == null || ssh.getSshPort() <= 0)
                        ? 22
                        : ssh.getSshPort();

                ClusterNode node = new ClusterNode();
                node.setCluster(cluster);
                node.setName(dto.getName().trim());
                node.setIpAddress(dto.getIp().trim());
                node.setUsername(
                        dto.getUser() != null && !dto.getUser().trim().isEmpty()
                                ? dto.getUser().trim()
                                : "luanvan"
                );

                node.setRole(NodeRole.valueOf(dto.getRole().toUpperCase()));
                node.setSshPort(sshPort);

                nodes.add(node);
                nodePasswords.put(node.getName(), ssh.getPassword().trim());
            }

            cluster.setClusterNodes(nodes);

            // =============================
            // 3. Deploy SSH key
            // =============================

            SshKeyService.DeployResult keyResult =
                    sshKeyService.deployAnsibleKeyToNodes(cluster, nodePasswords);

            if (!keyResult.allSuccess()) {

                StringBuilder error = new StringBuilder("Chuẩn bị node thất bại:\n");

                error.append("- Deploy public key lỗi ở: ")
                        .append(String.join(", ", keyResult.failed))
                        .append("\n");

                return ResponseEntity.badRequest().body(error.toString());
            }

            // =============================
            // 4. Lưu cluster vào DB
            // =============================

            Cluster savedCluster = clusterRepository.save(cluster);
            clusterNodeRepository.saveAll(nodes);

            // =============================
            // 5. Chạy deploy cluster background
            // =============================

            clusterDeployService.deployClusterAsync(savedCluster);

            // =============================
            // 6. Trả clusterId cho React
            // =============================

            return ResponseEntity.ok(
                    new ClusterCreateResponse(
                            savedCluster.getId(),
                            "Cluster đang được triển khai"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Lỗi hệ thống: " + e.getMessage());
        }
    }



    @PostMapping("/{clusterId}/workers")
    public ResponseEntity<?> addWorker(
            @PathVariable Long clusterId,
            @RequestBody ClusterCreationRequest request
    ) {

        try {

            ansibleService.addWorker(clusterId, request);

            return ResponseEntity.ok("Worker đã được thêm thành công");

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi khi thêm worker: " + e.getMessage());
        }
    }

    @DeleteMapping("/{clusterId}/nodes/{nodeName}")
    public ResponseEntity<String> deleteNode(
            @PathVariable Long clusterId,
            @PathVariable String nodeName) {

        try {

            ansibleService.removeWorker(clusterId, nodeName);

            return ResponseEntity.ok(
                    "Worker '" + nodeName + "' được xóa khỏi cluster " + clusterId
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected error while removing node: " + nodeName);
        }
    }


}
