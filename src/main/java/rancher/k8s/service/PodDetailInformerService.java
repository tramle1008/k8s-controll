package rancher.k8s.service;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import rancher.k8s.dto.PodDetailDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PodDetailInformerService {

    private final KubernetesClient client;
    private final SimpMessagingTemplate messagingTemplate;

    private SharedIndexInformer<Pod> informer;

    @PostConstruct
    public void startInformer() {
        log.info("Starting PodDetailInformerService...");

        informer = client.pods()
                .inAnyNamespace()
                .inform(new ResourceEventHandler<Pod>() {
                    @Override
                    public void onAdd(Pod pod) {
                        handlePodChange(pod, "ADDED");
                    }

                    @Override
                    public void onUpdate(Pod oldPod, Pod newPod) {
                        handlePodChange(newPod, "MODIFIED");
                    }

                    @Override
                    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                        handlePodChange(pod, "DELETED");
                    }
                }, 600_000L);  // resync period: 10 phút

        // Chạy informer trong thread riêng
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                informer.run();
                log.info("PodDetail informer is running");
            } catch (Exception e) {
                log.error("PodDetail informer crashed", e);
            }
        });

        // Reconcile ban đầu sau khi informer khởi động
        Executors.newSingleThreadScheduledExecutor()
                .schedule(this::reconcileInitialPods, 8, java.util.concurrent.TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (informer != null) {
            informer.stop();
            log.info("PodDetailInformerService stopped");
        }
    }

    private void handlePodChange(Pod pod, String eventType) {
        if (pod == null || pod.getMetadata() == null) {
            return;
        }

        String namespace = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();

        PodDetailDto dto = buildPodDetailDto(pod);

        if ("DELETED".equals(eventType)) {
            dto.setStatus("Deleted");
            dto.setReady("0/0");
            dto.setRestarts(0);  // reset hoặc giữ nếu cần lịch sử
            // Có thể thêm field deletedAt nếu frontend cần
        }

        // Push thay đổi đến channel namespace-specific (tối ưu bandwidth)
        String namespaceTopic = "/topic/pods/" + namespace;
        messagingTemplate.convertAndSend(namespaceTopic, dto);

        // Nếu frontend cần global view (all namespaces), push thêm vào channel chung
        messagingTemplate.convertAndSend("/topic/pods-all", dto);

        log.debug("{} pod: {}/{} - status: {}, ready: {}, restarts: {}",
                eventType, namespace, name, dto.getStatus(), dto.getReady(), dto.getRestarts());
    }

    private void reconcileInitialPods() {
        log.info("Starting initial reconciliation for PodDetailDto...");
        try {
            var podList = client.pods().inAnyNamespace().list().getItems();
            int processed = 0;

            for (Pod pod : podList) {
                handlePodChange(pod, "RECONCILE");
                processed++;
            }

            log.info("Reconciled {} pods successfully", processed);
        } catch (Exception e) {
            log.error("Reconciliation failed", e);
        }
    }

    private PodDetailDto buildPodDetailDto(Pod pod) {
        PodDetailDto dto = new PodDetailDto();

        dto.setNamespace(pod.getMetadata().getNamespace());
        dto.setName(pod.getMetadata().getName());

        // READY & RESTARTS
        int readyCount = 0;
        int totalContainers = 0;
        int totalRestarts = 0;

        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            List<ContainerStatus> statuses = pod.getStatus().getContainerStatuses();
            totalContainers = statuses.size();
            for (ContainerStatus cs : statuses) {
                if (Boolean.TRUE.equals(cs.getReady())) {
                    readyCount++;
                }
                if (cs.getRestartCount() != null) {
                    totalRestarts += cs.getRestartCount();
                }
            }
        }

        dto.setReady(readyCount + "/" + totalContainers);
        dto.setRestarts(totalRestarts);

        // STATUS
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        dto.setStatus(phase);

        // Có thể enhance status ở đây nếu cần (ví dụ CrashLoopBackOff)
        // if ("Running".equals(phase) && totalRestarts > 3 && readyCount < totalContainers) {
        //     dto.setStatus("CrashLoopBackOff");
        // }

        // AGE
        dto.setAge(calculateHumanReadableAge(pod.getMetadata().getCreationTimestamp()));

        // Creation timestamp cho sort/tooltip
        dto.setCreationTimestamp(
                pod.getMetadata().getCreationTimestamp() != null
                        ? Instant.parse(pod.getMetadata().getCreationTimestamp())
                        : null
        );

        dto.setNodeName(pod.getSpec() != null ? pod.getSpec().getNodeName() : null);
        dto.setPodIp(pod.getStatus() != null ? pod.getStatus().getPodIP() : null);
        dto.setLabels(pod.getMetadata().getLabels() != null
                ? pod.getMetadata().getLabels()
                : new java.util.HashMap<>());

        return dto;
    }

    private String calculateHumanReadableAge(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return "N/A";
        }

        try {
            Instant created = Instant.parse(timestampStr);
            Duration duration = Duration.between(created, Instant.now());

            if (duration.isNegative() || duration.isZero()) {
                return "<1s";
            }

            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;

            StringBuilder sb = new StringBuilder();
            if (days > 0) sb.append(days).append("d");
            if (hours > 0) sb.append(hours).append("h");
            if (minutes > 0) sb.append(minutes).append("m");

            return sb.length() > 0 ? sb.toString() : "<1m";
        } catch (Exception e) {
            log.warn("Invalid timestamp format: {}", timestampStr);
            return "?";
        }
    }
}