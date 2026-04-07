package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import infra.k8s.dto.pvc.PVCRequest;
import infra.k8s.dto.pvc.PVCResponse;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.PVCService;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PVCServiceImpl implements PVCService {

    private final ClusterManager clusterManager;

    private KubernetesClient getClient() {
        return clusterManager.requireActiveClient();
    }

    @Override
    public List<PVCResponse> getAll() {
        KubernetesClient client = getClient();

        List<PersistentVolumeClaim> pvcs = client.persistentVolumeClaims()
                .inAnyNamespace()
                .list()
                .getItems();

        List<PVCResponse> result = new ArrayList<>();

        for (PersistentVolumeClaim pvc : pvcs) {
            String accessModes = pvc.getSpec() != null && pvc.getSpec().getAccessModes() != null
                    ? String.join(", ", pvc.getSpec().getAccessModes())
                    : "";

            String storageClass = pvc.getSpec() != null
                    ? pvc.getSpec().getStorageClassName()
                    : null;

            String volumeName = pvc.getSpec() != null
                    ? pvc.getSpec().getVolumeName()
                    : null;

            String capacity = null;
            if (pvc.getStatus() != null
                    && pvc.getStatus().getCapacity() != null
                    && pvc.getStatus().getCapacity().get("storage") != null) {
                capacity = pvc.getStatus().getCapacity().get("storage").getAmount();
            } else if (pvc.getSpec() != null
                    && pvc.getSpec().getResources() != null
                    && pvc.getSpec().getResources().getRequests() != null
                    && pvc.getSpec().getResources().getRequests().get("storage") != null) {
                capacity = pvc.getSpec().getResources().getRequests().get("storage").getAmount();
            }

            String status = pvc.getStatus() != null ? pvc.getStatus().getPhase() : "Unknown";

            String age = getAge(
                    pvc.getMetadata() != null ? pvc.getMetadata().getCreationTimestamp() : null
            );

            result.add(new PVCResponse(
                    pvc.getMetadata() != null ? pvc.getMetadata().getName() : "",
                    pvc.getMetadata() != null ? pvc.getMetadata().getNamespace() : "",
                    status,
                    volumeName,
                    capacity,
                    accessModes,
                    storageClass,
                    age
            ));
        }

        return result;
    }

    @Override
    public PersistentVolumeClaim create(PVCRequest request) {
        KubernetesClient client = getClient();

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Tên PVC không được để trống");
        }

        if (request.getStorage() == null || request.getStorage().isBlank()) {
            throw new RuntimeException("Storage không được để trống");
        }

        if (request.getAccessModes() == null || request.getAccessModes().isEmpty()) {
            throw new RuntimeException("AccessModes không được để trống");
        }

        String namespace = (request.getNamespace() == null || request.getNamespace().isBlank())
                ? "default"
                : request.getNamespace();

        PersistentVolumeClaimBuilder builder = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(request.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withAccessModes(request.getAccessModes())
                .withNewResources()
                .addToRequests("storage", new Quantity(request.getStorage()))
                .endResources()
                .endSpec();

        if (request.getStorageClassName() != null && !request.getStorageClassName().isBlank()) {
            builder.editSpec()
                    .withStorageClassName(request.getStorageClassName())
                    .endSpec();
        }

        if (request.getVolumeName() != null && !request.getVolumeName().isBlank()) {
            builder.editSpec()
                    .withVolumeName(request.getVolumeName())
                    .endSpec();
        }

        PersistentVolumeClaim pvc = builder.build();

        return client.persistentVolumeClaims()
                .inNamespace(namespace)
                .resource(pvc)
                .create();
    }

    @Override
    public boolean delete(String namespace, String name) {
        KubernetesClient client = getClient();

        PersistentVolumeClaim existing = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (existing == null) {
            return false;
        }

        return Boolean.TRUE.equals(
                client.persistentVolumeClaims()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                        .size() > 0
        );
    }

    @Override
    public void createFromFile(MultipartFile file) {
        KubernetesClient client = getClient();

        try (InputStream is = file.getInputStream()) {
            client.load(is).createOrReplace();
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo PVC từ file YAML", e);
        }
    }

    @Override
    public String getRawYaml(String namespace, String name) {
        KubernetesClient client = getClient();

        PersistentVolumeClaim pvc = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (pvc == null) {
            return null;
        }

        return Serialization.asYaml(pvc);
    }

    private String getAge(String creationTimestamp) {
        if (creationTimestamp == null || creationTimestamp.isBlank()) {
            return "";
        }

        try {
            OffsetDateTime created = OffsetDateTime.parse(creationTimestamp);
            Duration duration = Duration.between(created, OffsetDateTime.now());

            long days = duration.toDays();
            if (days > 0) return days + "d";

            long hours = duration.toHours();
            if (hours > 0) return hours + "h";

            long minutes = duration.toMinutes();
            if (minutes > 0) return minutes + "m";

            return duration.getSeconds() + "s";
        } catch (Exception e) {
            return "";
        }
    }
}