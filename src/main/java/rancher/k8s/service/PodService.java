package rancher.k8s.service;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rancher.k8s.dto.PodDetailDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PodService {

    private final KubernetesClient client;

    @Autowired
    public PodService(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Lấy tất cả Pods ở mọi namespace và map sang DTO
     */
    public List<PodDetailDto> getAllPodDetails() {
        PodList podList = client.pods().inAnyNamespace().list();
        return podList.getItems().stream()
                .map(this::mapToPodDetailDto)
                .collect(Collectors.toList());
    }

    /**
     * Lấy Pods theo namespace cụ thể
     */
    public List<PodDetailDto> getPodDetailsByNamespace(String namespace) {
        if (namespace == null || "all".equalsIgnoreCase(namespace)) {
            return getAllPodDetails();
        }
        PodList podList = client.pods().inNamespace(namespace).list();
        return podList.getItems().stream()
                .map(this::mapToPodDetailDto)
                .collect(Collectors.toList());
    }

    private PodDetailDto mapToPodDetailDto(Pod pod) {
        PodDetailDto dto = new PodDetailDto();

        dto.setNamespace(pod.getMetadata().getNamespace());
        dto.setName(pod.getMetadata().getName());

        // READY: readyCount / totalContainers
        int readyCount = 0;
        int totalContainers = 0;
        int totalRestarts = 0;
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                totalContainers++;
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

        // STATUS: phase cơ bản, có thể enhance sau
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        dto.setStatus(phase);  // Nếu muốn chi tiết hơn (CrashLoopBackOff...), thêm logic ở đây

        // AGE: format giống kubectl
        dto.setAge(calculateHumanAge(pod.getMetadata().getCreationTimestamp()));
        dto.setCreationTimestamp(
                pod.getMetadata().getCreationTimestamp() != null
                        ? Instant.parse(pod.getMetadata().getCreationTimestamp())
                        : null
        );

        dto.setNodeName(pod.getSpec() != null ? pod.getSpec().getNodeName() : null);
        dto.setPodIp(pod.getStatus() != null ? pod.getStatus().getPodIP() : null);
        dto.setLabels(pod.getMetadata().getLabels());

        // Optional: images & ports
        List<String> images = new ArrayList<>();
        List<String> portsList = new ArrayList<>();
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            for (Container c : pod.getSpec().getContainers()) {
                images.add(c.getImage());
                if (c.getPorts() != null) {
                    for (ContainerPort cp : c.getPorts()) {
                        String portStr = cp.getContainerPort() + "/" + (cp.getProtocol() != null ? cp.getProtocol() : "TCP");
                        if (cp.getName() != null && !cp.getName().isEmpty()) {
                            portStr += " (" + cp.getName() + ")";
                        }
                        portsList.add(portStr);
                    }
                }
            }
        }


        return dto;
    }

    private String calculateHumanAge(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return "N/A";
        }
        try {
            Instant created = Instant.parse(timestampStr);
            Duration duration = Duration.between(created, Instant.now());
            if (duration.isNegative()) {
                return "0s";
            }

            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            StringBuilder sb = new StringBuilder();
            if (days > 0) sb.append(days).append("d");
            if (hours > 0) sb.append(hours).append("h");
            if (minutes > 0) sb.append(minutes).append("m");
            if (sb.length() == 0 && seconds > 0) sb.append(seconds).append("s");
            return sb.length() > 0 ? sb.toString() : "<1m";
        } catch (Exception e) {
            return "?";
        }
    }
}