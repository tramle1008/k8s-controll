package infra.k8s.service;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import infra.k8s.dto.PodDetailDto;
import infra.k8s.dto.PodSummaryDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PodService {


    private final ClusterManager clusterManager;
    @Autowired
    public PodService( ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    public PodSummaryDto buildSummaryFromK8s() {

        KubernetesClient client = clusterManager.getActiveClient();

        if (client == null) {
            return new PodSummaryDto();
        }

        List<Pod> pods = client.pods()
                .inAnyNamespace()
                .list()
                .getItems();

        PodSummaryDto summary = new PodSummaryDto();

        summary.setTotalPods(pods.size());

        summary.setRunningPods(
                pods.stream()
                        .filter(p -> p.getStatus() != null &&
                                "Running".equals(p.getStatus().getPhase()))
                        .count()
        );

        summary.setPendingPods(
                pods.stream()
                        .filter(p -> p.getStatus() != null &&
                                "Pending".equals(p.getStatus().getPhase()))
                        .count()
        );

        summary.setFailedPods(
                pods.stream()
                        .filter(p -> p.getStatus() != null &&
                                "Failed".equals(p.getStatus().getPhase()))
                        .count()
        );

        summary.setSucceededPods(
                pods.stream()
                        .filter(p -> p.getStatus() != null &&
                                "Succeeded".equals(p.getStatus().getPhase()))
                        .count()
        );

        summary.setUnknownPods(
                pods.stream()
                        .filter(p -> p.getStatus() == null ||
                                "Unknown".equals(p.getStatus().getPhase()))
                        .count()
        );

        summary.setAlertedPods(0); // nếu không dùng cache thì chưa có alert logic
        summary.setLastUpdated(Instant.now().toString());

        return summary;
    }

    /**
     * Lấy tất cả Pods ở mọi namespace và map sang DTO
     */
    public List<PodDetailDto> getAllPodDetails() {

        KubernetesClient client = clusterManager.requireActiveClient();

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
        KubernetesClient client = clusterManager.requireActiveClient();


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

    public String getPodLogs(String namespace, String podName, String container) {
        KubernetesClient client = clusterManager.requireActiveClient();
        try {

            if (container != null && !container.isBlank()) {
                return client.pods()
                        .inNamespace(namespace)
                        .withName(podName)
                        .inContainer(container)
                        .getLog();
            }

            return client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .getLog();

        } catch (Exception e) {
            String msg = e.getMessage();

            if (msg != null && msg.contains("Message:")) {
                msg = msg.split("Message:")[1].split("Received status")[0].trim();
            }

            return "Container chưa có log\n\n" + msg;
        }
    }
}