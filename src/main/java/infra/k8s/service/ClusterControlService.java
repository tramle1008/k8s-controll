package infra.k8s.service;
import infra.k8s.dto.cluster.AssignClusterRequest;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.ClusterDto;
import infra.k8s.dto.cluster.ClusterImportRequest;
import infra.k8s.dto.cluster.ClusterManagementDto;

import java.util.List;


public interface ClusterControlService {
    List<ClusterDto> getAllClusterActive();
    List<ClusterManagementDto> getAllClustersForManagement();
    ClusterManagementDto getClusterDetail(Long id);
    ClusterManagementDto importCluster(ClusterImportRequest request, MultipartFile adminConfFile);
    void deleteCluster(Long id);
    void assignClusterToUser(AssignClusterRequest request);
}