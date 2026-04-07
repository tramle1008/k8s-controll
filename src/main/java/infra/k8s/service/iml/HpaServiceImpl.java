package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.hpa.ConditionDto;
import infra.k8s.dto.hpa.HpaCreateRequest;
import infra.k8s.dto.hpa.HpaDescribeDto;
import infra.k8s.dto.hpa.HpaDto;
import infra.k8s.dto.mapper.HpaMapper;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.HpaService;
import infra.k8s.util.TimeUtils;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HpaServiceImpl implements HpaService {

    private final ClusterManager clusterManager;

    @Override
    public List<HpaDto> list() {

        KubernetesClient client = clusterManager.requireActiveClient();

        return client.autoscaling()
                .v2()
                .horizontalPodAutoscalers()
                .inAnyNamespace()
                .list()
                .getItems()
                .stream()
                .map(this::toDto)
                .toList();
    }
    private HpaDto toDto(HorizontalPodAutoscaler hpa) {

        String reference =
                hpa.getSpec().getScaleTargetRef().getKind() + "/" +
                        hpa.getSpec().getScaleTargetRef().getName();

        Integer minPods = hpa.getSpec().getMinReplicas();

        Integer maxPods = hpa.getSpec().getMaxReplicas();

        Integer current = hpa.getStatus().getCurrentReplicas();
        Integer desired = hpa.getStatus().getDesiredReplicas();

        String replicas = current + "/" + desired;

        String age = TimeUtils.calculateHumanAge(
                hpa.getMetadata().getCreationTimestamp()
        );

        return new HpaDto(
                hpa.getMetadata().getName(),
                hpa.getMetadata().getNamespace(),
                reference,
                buildTarget(hpa),
                minPods,
                maxPods,
                replicas,
                age
        );
    }
    private String buildTarget(HorizontalPodAutoscaler hpa) {

        if (hpa.getSpec().getMetrics() == null) {
            return "-";
        }

        StringBuilder result = new StringBuilder();

        for (var metric : hpa.getSpec().getMetrics()) {

            if ("Resource".equals(metric.getType())) {

                String resourceName = metric.getResource().getName();

                Integer target =
                        metric.getResource()
                                .getTarget()
                                .getAverageUtilization();

                Integer current = null;

                if (hpa.getStatus() != null &&
                        hpa.getStatus().getCurrentMetrics() != null) {

                    for (var currentMetric : hpa.getStatus().getCurrentMetrics()) {

                        if ("Resource".equals(currentMetric.getType()) &&
                                resourceName.equals(currentMetric.getResource().getName())) {

                            current =
                                    currentMetric
                                            .getResource()
                                            .getCurrent()
                                            .getAverageUtilization();
                        }
                    }
                }

                result.append(resourceName)
                        .append(": ")
                        .append(current != null ? current : "?")
                        .append("%/")
                        .append(target)
                        .append("%");
            }
        }

        return result.toString();
    }

    @Override
    public HorizontalPodAutoscaler get(String namespace, String name) {
        KubernetesClient client = clusterManager.requireActiveClient();

        return client.autoscaling()
                .v2()
                .horizontalPodAutoscalers()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    @Override
    public ResponseEntity<String> create(HpaCreateRequest request) {

        try {

            KubernetesClient client = clusterManager.requireActiveClient();

            String namespace = request.getMetadata().getNamespace();
            String targetName = request.getSpec().getTargetName();
            String targetKind = request.getSpec().getTargetKind();

            var existing = client.autoscaling()
                    .v2()
                    .horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            boolean exists = existing.stream()
                    .anyMatch(h -> {
                        var ref = h.getSpec().getScaleTargetRef();
                        return ref != null
                                && targetKind.equalsIgnoreCase(ref.getKind())
                                && targetName.equals(ref.getName());
                    });

            if (exists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("HPA already exists for " + targetKind + " " + targetName);
            }

            HorizontalPodAutoscaler hpa = HpaMapper.toK8s(request);

            client.autoscaling()
                    .v2()
                    .horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .resource(hpa)
                    .create();

            return ResponseEntity.ok("Tạo thành công HPA: " + request.getMetadata().getName());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create HPA: " + e.getMessage());
        }
    }
    @Override
    public void delete(String namespace, String name) {
        KubernetesClient client = clusterManager.requireActiveClient();
        client.autoscaling()
                .v2()
                .horizontalPodAutoscalers()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    @Override
    public String createHpaFromYaml(MultipartFile yamlFile) {

        try (InputStream is = yamlFile.getInputStream()) {
            KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
            HorizontalPodAutoscaler hpa =
                    Serialization.unmarshal(is, HorizontalPodAutoscaler.class);

            if (hpa.getMetadata() == null || hpa.getMetadata().getName() == null) {
                throw new IllegalArgumentException("HPA name is required");
            }

            String namespace =
                    hpa.getMetadata().getNamespace() == null
                            ? "default"
                            : hpa.getMetadata().getNamespace();

            kubernetesClient.autoscaling()
                    .v2()
                    .horizontalPodAutoscalers()
                    .inNamespace(namespace)
                    .resource(hpa)
                    .create();

            return hpa.getMetadata().getName();

        } catch (Exception e) {

            throw new RuntimeException("Failed to import HPA YAML: " + e.getMessage());

        }
    }

    @Override
    public HpaDescribeDto describeHpa(String namespace, String name) {

        KubernetesClient client = clusterManager.requireActiveClient();

        HorizontalPodAutoscaler hpa =
                client.autoscaling()
                        .v2()
                        .horizontalPodAutoscalers()
                        .inNamespace(namespace)
                        .withName(name)
                        .get();

        if (hpa == null) {
            throw new RuntimeException("HPA not found");
        }

        String reference =
                hpa.getSpec().getScaleTargetRef().getKind()
                        + "/"
                        + hpa.getSpec().getScaleTargetRef().getName();

        String metrics = "";

        if (hpa.getStatus() != null && hpa.getStatus().getCurrentMetrics() != null) {

            metrics = hpa.getStatus().getCurrentMetrics()
                    .stream()
                    .map(metric -> {

                        if (metric.getResource() == null) return "";

                        String resource = metric.getResource().getName();

                        Integer current =
                                metric.getResource()
                                        .getCurrent()
                                        .getAverageUtilization();

                        String currentStr =
                                current == null ? "<unknown>" : current + "%";

                        Integer target =
                                hpa.getSpec().getMetrics()
                                        .stream()
                                        .filter(m -> m.getResource() != null &&
                                                m.getResource().getName().equals(resource))
                                        .findFirst()
                                        .map(m -> m.getResource().getTarget().getAverageUtilization())
                                        .orElse(null);

                        String targetStr =
                                target == null ? "<unknown>" : target + "%";

                        return resource + ": " + currentStr + " / " + targetStr;

                    })
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(", "));
        }

        Integer currentPods =
                hpa.getStatus() != null ? hpa.getStatus().getCurrentReplicas() : null;

        Integer desiredPods =
                hpa.getStatus() != null ? hpa.getStatus().getDesiredReplicas() : null;

        String pods =
                (currentPods == null ? 0 : currentPods)
                        + " current / "
                        + (desiredPods == null ? 0 : desiredPods)
                        + " desired";

        List<ConditionDto> conditions =
                hpa.getStatus() != null && hpa.getStatus().getConditions() != null
                        ? hpa.getStatus().getConditions()
                        .stream()
                        .map(c ->
                                ConditionDto.builder()
                                        .type(c.getType())
                                        .status(c.getStatus())
                                        .reason(c.getReason())
                                        .message(c.getMessage())
                                        .build()
                        )
                        .collect(Collectors.toList())
                        : List.of();

        return HpaDescribeDto.builder()
                .name(hpa.getMetadata().getName())
                .namespace(hpa.getMetadata().getNamespace())
                .creationTimestamp(hpa.getMetadata().getCreationTimestamp())
                .reference(reference)
                .metrics(metrics)
                .minReplicas(hpa.getSpec().getMinReplicas())
                .maxReplicas(hpa.getSpec().getMaxReplicas())
                .pods(pods)
                .conditions(conditions)
                .build();
    }
}
