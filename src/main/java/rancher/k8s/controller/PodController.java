package rancher.k8s.controller;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import rancher.k8s.cache.PodStateCache;
import rancher.k8s.dto.PodDetailDto;
import rancher.k8s.dto.PodSummaryDto;
import rancher.k8s.module.PodState;
import rancher.k8s.service.PodService;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/api/pods")
public class PodController {
    private final PodStateCache cache;
    private final PodService podService;
    private final KubernetesClient client;

    // Constructor duy nhất, inject cả hai
    @Autowired
    public PodController(PodStateCache cache, PodService podService, KubernetesClient client) {
        this.cache = cache;
        this.podService = podService;
        this.client = client;
    }

    @GetMapping("/summary")
    public PodSummaryDto getCurrentPodSummary() {
        PodSummaryDto summary = new PodSummaryDto();
        Collection<PodState> all = cache.getAll();

        summary.setTotalPods(cache.getTotalCount());
        summary.setRunningPods(cache.getRunningCount());
        summary.setPendingPods(all.stream().filter(s -> "Pending".equals(s.getPhase())).count());
        summary.setFailedPods(all.stream().filter(s -> "Failed".equals(s.getPhase())).count());
        summary.setSucceededPods(all.stream().filter(s -> "Succeeded".equals(s.getPhase())).count());
        summary.setUnknownPods(all.stream().filter(s -> "Unknown".equals(s.getPhase())).count());
        summary.setAlertedPods(cache.getAlertedCount());
        summary.setLastUpdated(Instant.now().toString());

        return summary;
    }

    /**
     * Lấy tất cả pods chi tiết (tương tự kubectl get po -A)
     */
    @GetMapping("/details")
    public List<PodDetailDto> getAllPodDetails() {
        return podService.getAllPodDetails();
    }

    /**
     * Lấy pods theo namespace (hoặc all nếu truyền "all")
     */
    @GetMapping("/details/namespace/{namespace}")
    public List<PodDetailDto> getPodDetailsByNamespace(@PathVariable String namespace) {
        return podService.getPodDetailsByNamespace(namespace);
    }



    // Trong endpoint deletePod của PodController
    @DeleteMapping("/{namespace}/{podName}")
    public ResponseEntity<String> deletePod(
            @PathVariable String namespace,
            @PathVariable String podName) {

        try {
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
}