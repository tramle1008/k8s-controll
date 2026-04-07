package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.Context.NodeRole;
import infra.k8s.dto.NodeDto;
import infra.k8s.dto.SshDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.ClusterNode;

       // giả định bạn có service này
import infra.k8s.service.SshKeyService;
import infra.k8s.dto.ClusterCreationRequest;
import infra.k8s.dto.SshValidationResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Slf4j
@RestController
@RequestMapping("/api/create")
@RequiredArgsConstructor
public class SshController {

        private final SshKeyService sshKeyService;

        @PostMapping("/ssh")
        public ResponseEntity<SshValidationResponse> validateAndDeploySshKeys(
                @RequestBody ClusterCreationRequest request) {

            if (request.getClusterName() == null || request.getClusterName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new SshValidationResponse(false, "Tên cluster là bắt buộc", null, 0));
            }

            if (request.getNodes() == null || request.getNodes().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new SshValidationResponse(false, "Cần ít nhất một node", request.getClusterName(), 0));
            }

            SshValidationResponse response = new SshValidationResponse();
            response.setClusterName(request.getClusterName());
            response.setNodeCount(request.getNodes().size());

            try {
                Cluster tempCluster = new Cluster();
                tempCluster.setName(request.getClusterName().trim());
                tempCluster.setStatus(ClusterStatus.PENDING);

                List<ClusterNode> clusterNodes = new ArrayList<>();
                Map<String, String> nodePasswords = new HashMap<>();

                for (NodeDto dto : request.getNodes()) {
                    if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Node name là bắt buộc",
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    if (dto.getIp() == null || dto.getIp().trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Node " + dto.getName() + " chưa có IP",
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    if (dto.getRole() == null || dto.getRole().trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Node " + dto.getName() + " chưa có role",
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    if (dto.getSsh() == null) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Node " + dto.getName() + " chưa có cấu hình SSH",
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    SshDto ssh = dto.getSsh();

                    if (!"password".equalsIgnoreCase(ssh.getMethod())) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Hiện tại chỉ hỗ trợ SSH method = password cho node " + dto.getName(),
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    if (ssh.getPassword() == null || ssh.getPassword().trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(new SshValidationResponse(false,
                                        "Node " + dto.getName() + " chưa có SSH password",
                                        request.getClusterName(),
                                        request.getNodes().size()));
                    }

                    Integer sshPort = (ssh.getSshPort() == null || ssh.getSshPort() <= 0)
                            ? 22
                            : ssh.getSshPort();

                    ClusterNode node = new ClusterNode();
                    node.setCluster(tempCluster);
                    node.setName(dto.getName().trim());
                    node.setIpAddress(dto.getIp().trim());
                    node.setUsername(dto.getUser() != null && !dto.getUser().trim().isEmpty()
                            ? dto.getUser().trim()
                            : "luanvan");
                    node.setRole(NodeRole.valueOf(dto.getRole().trim().toUpperCase()));
                    node.setSshPort(sshPort);

                    clusterNodes.add(node);
                    nodePasswords.put(node.getName(), ssh.getPassword().trim());
                }

                tempCluster.setClusterNodes(clusterNodes);

                SshKeyService.DeployResult keyResult =
                        sshKeyService.deployAnsibleKeyToNodes(tempCluster, nodePasswords);

                response.getSuccessfulNodes().addAll(keyResult.success);
                response.getFailedNodes().addAll(keyResult.failed);

                if (!keyResult.allSuccess()) {
                    response.setSuccess(false);
                    response.setMessage("Có lỗi trong quá trình deploy public key lên một số node");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }

                response.setSuccess(true);
                response.setMessage("Đã deploy public key Ansible thành công cho tất cả "
                        + clusterNodes.size() + " node. Bạn có thể tiến hành tạo cluster.");

                return ResponseEntity.ok(response);

            } catch (IllegalArgumentException e) {
                log.error("Role không hợp lệ: {}", e.getMessage(), e);
                response.setSuccess(false);
                response.setMessage("Role node không hợp lệ. Chỉ chấp nhận master hoặc worker.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

            } catch (Exception e) {
                log.error("Lỗi tổng thể khi xử lý request cho cluster {}: {}",
                        request.getClusterName(), e.getMessage(), e);
                response.setSuccess(false);
                response.setMessage("Lỗi hệ thống: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }
}
