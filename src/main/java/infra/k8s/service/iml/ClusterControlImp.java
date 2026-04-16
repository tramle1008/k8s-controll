package infra.k8s.service.iml;

import infra.k8s.Context.UserRole;
import infra.k8s.dto.cluster.*;
import infra.k8s.module.User;
import infra.k8s.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.dto.ClusterDto;
import infra.k8s.module.Cluster;
import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.ClusterControlService;
import infra.k8s.service.CryptoService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClusterControlImp implements ClusterControlService {

    private final ClusterRepository clusterRepository;
    private final CryptoService cryptoService;
    private final UserRepository userRepository;

    @Override
    public List<ClusterDto> getAllClusterActive() {
        return clusterRepository
                .findByStatus(ClusterStatus.ACTIVE)
                .stream()
                .map(cluster -> new ClusterDto(
                        cluster.getId(),
                        cluster.getName()
                ))
                .toList();
    }

    @Override
    public List<ClusterManagementDto> getAllClustersForManagement() {
        return clusterRepository.findByStatus(ClusterStatus.ACTIVE)
                .stream()
                .map(this::toManagementDto)
                .toList();
    }

    @Override
    public ClusterManagementDto getClusterDetail(Long clusterId) {
        Cluster cluster = clusterRepository.findClusterWithNodes(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại với id: " + clusterId));

        return toManagementDto(cluster);
    }

    @Override
    public ClusterManagementDto importCluster(ClusterImportRequest request, MultipartFile adminConfFile) {
        validateImportRequest(request);
        validateAdminConfFile(adminConfFile);

        if (clusterRepository.findByName(request.getClusterName()).isPresent()) {
            throw new RuntimeException("Tên cluster đã tồn tại: " + request.getClusterName());
        }

        try {
            String adminConfContent = new String(adminConfFile.getBytes(), StandardCharsets.UTF_8);

            if (adminConfContent.isBlank()) {
                throw new RuntimeException("Nội dung file admin.conf đang rỗng");
            }

            String encrypted = cryptoService.encrypt(adminConfContent);

            Cluster cluster = new Cluster();
            cluster.setName(request.getClusterName());

            cluster.setStatus(ClusterStatus.ACTIVE);
            cluster.setEncryptedKubeconfig(encrypted);
            cluster.setClusterNodes(new ArrayList<>());

            for (ClusterImportRequest.NodeImportRequest nodeReq : request.getNodes()) {
                ClusterNode node = new ClusterNode();
                node.setCluster(cluster);
                node.setName(nodeReq.getName());
                node.setIpAddress(nodeReq.getIpAddress());
                node.setRole(nodeReq.getRole());
                node.setUsername(
                        nodeReq.getUsername() != null && !nodeReq.getUsername().isBlank()
                                ? nodeReq.getUsername()
                                : "ubuntu"
                );
                node.setReady(false);
                node.setAlerted(false);
                node.setUpdatedAt(Instant.now());

                cluster.getClusterNodes().add(node);
            }

            Cluster savedCluster = clusterRepository.saveAndFlush(cluster);

            Cluster fullCluster = clusterRepository.findClusterWithNodes(savedCluster.getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cluster sau khi tạo"));

            return toManagementDto(fullCluster);

        } catch (Exception e) {
            throw new RuntimeException("Import cluster thất bại: " + e.getMessage(), e);
        }
    }

    private void validateAdminConfFile(MultipartFile adminConfFile) {
        if (adminConfFile == null || adminConfFile.isEmpty()) {
            throw new RuntimeException("File admin.conf không được để trống");
        }

        String originalFilename = adminConfFile.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new RuntimeException("Tên file không hợp lệ");
        }
    }

    @Override
    public void deleteCluster(Long clusterId) {
        Cluster cluster = clusterRepository.findClusterWithNodes(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster không tồn tại với id: " + clusterId));

        clusterRepository.delete(cluster);
    }

    @Override
    public void assignClusterToUser(AssignClusterRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getUserRole() == UserRole.ADMIN) {
            throw new AccessDeniedException("Cannot assign cluster to Admin user");
        }

        if (user.getCluster() != null) {
            throw new IllegalStateException("User already assigned to a cluster");
        }

        Cluster cluster = clusterRepository.findById(request.getClusterId())
                .orElseThrow(() -> new EntityNotFoundException("Cluster not found"));

        user.setCluster(cluster);
        userRepository.save(user);
    }

    private void validateImportRequest(ClusterImportRequest request) {
        if (request == null) {
            throw new RuntimeException("Request không được null");
        }

        if (request.getClusterName() == null || request.getClusterName().isBlank()) {
            throw new RuntimeException("Tên cluster không được để trống");
        }

        if (request.getNodes() == null || request.getNodes().isEmpty()) {
            throw new RuntimeException("Cluster phải có ít nhất 1 node");
        }

        for (ClusterImportRequest.NodeImportRequest node : request.getNodes()) {
            if (node.getName() == null || node.getName().isBlank()) {
                throw new RuntimeException("Tên node không được để trống");
            }
            if (node.getIpAddress() == null || node.getIpAddress().isBlank()) {
                throw new RuntimeException("IP node không được để trống");
            }
            if (node.getRole() == null) {
                throw new RuntimeException("Vai trò node không được để trống");
            }
        }
    }

    private ClusterManagementDto toManagementDto(Cluster cluster) {
        List<ClusterNodeResponseDto> nodeDtos = cluster.getClusterNodes() == null
                ? List.of()
                : cluster.getClusterNodes().stream()
                .map(node -> new ClusterNodeResponseDto(
                        node.getId(),
                        node.getName(),
                        node.getRole(),
                        node.getIpAddress(),
                        node.getUsername()
                ))
                .toList();
        // Tìm userRole = USER trong cluster
        UserDto userOwner = null;

        if (cluster.getUsers() != null) {
            userOwner = cluster.getUsers().stream()
                    .filter(u -> u.getUserRole() == UserRole.USER)
                    .findFirst()
                    .map(u -> new UserDto(
                            u.getId(),
                            u.getUsername()
                    ))
                    .orElse(null);
        }

        return new ClusterManagementDto(
                cluster.getId(),
                cluster.getName(),
                cluster.getCreatedAt(),
                cluster.getUpdatedAt(),
                nodeDtos.size(),
                userOwner,
                nodeDtos
        );
    }

}