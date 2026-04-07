package infra.k8s.controller;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import infra.k8s.dto.PodDetailDto;
import infra.k8s.dto.PodSummaryDto;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.PodService;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/pods")
public class PodController {

    private final PodService podService;
    private final ClusterManager clusterManager;

    // Constructor duy nhất, inject cả hai
    @Autowired
    public PodController( PodService podService, ClusterManager clusterManager) {
        this.podService = podService;
        this.clusterManager = clusterManager;
    }

    @GetMapping("/summary")
    public PodSummaryDto getCurrentPodSummary() {

        Long clusterId = clusterManager.getActiveClusterId();

        if (clusterId == null) {
            return new PodSummaryDto();
        }

        return podService.buildSummaryFromK8s();
    }
    /**
     * Lấy tất cả pods chi tiết (tương tự kubectl get po -A)
     */
    @GetMapping("/details")
    public List<PodDetailDto> getAllPodDetails() {
        return podService.getAllPodDetails();
    }



//    /**
//     * Lấy pods theo namespace (hoặc all nếu truyền "all")
//     */
    @GetMapping("/details/namespace/{namespace}")
    public List<PodDetailDto> getPodDetailsByNamespace(@PathVariable String namespace) {
        return podService.getPodDetailsByNamespace(namespace);
    }




    @DeleteMapping("/{namespace}/{podName}")
    public ResponseEntity<String> deletePod(
            @PathVariable String namespace,
            @PathVariable String podName) {

        try {
            KubernetesClient client = clusterManager.getActiveClient();
            // Gọi delete với tùy chọn force (grace period 0) và propagation
            List<StatusDetails> results = client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .withGracePeriod(0)                      // Force delete ngay lập tức
                    .withPropagationPolicy(DeletionPropagation.FOREGROUND)  // Hoặc BACKGROUND nếu muốn
                    .delete();

            // Kiểm tra kết quả
            if (results == null || results.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Không có pod và namespace phù hợp");
            }

        } catch (KubernetesClientException e) {
            log.error("KubernetesClientException khi xóa pod {}/{}", namespace, podName, e);
            return ResponseEntity.status(e.getCode()).body("Lỗi từ Kubernetes API: " + e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi bất ngờ khi xóa pod {}/{}", namespace, podName, e);
            return ResponseEntity.internalServerError().body("Lỗi server: " + e.getMessage());
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Xóa thành công");
    }

    @GetMapping("/{namespace}/{podName}/logs")
    public ResponseEntity<String> getPodLogs(
            @PathVariable String namespace,
            @PathVariable String podName,
            @RequestParam(required = false) String container
    ) {

        String logs = podService.getPodLogs(namespace, podName, container);

        return ResponseEntity.ok(logs);
    }

}