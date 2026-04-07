package infra.k8s.service.iml;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.LocalVolumeSource;
import io.fabric8.kubernetes.api.model.NFSVolumeSource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import infra.k8s.dto.PV.PVRequest;
import infra.k8s.dto.PV.PVResponse;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.PVService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PVServiceIml implements PVService {

    private final ClusterManager clusterManager;

    private KubernetesClient getKubernetesClient() {
        return clusterManager.requireActiveClient();
    }

    @Override
    public List<PVResponse> getAll() {
        List<PersistentVolume> pvs = getKubernetesClient()
                .persistentVolumes()
                .list()
                .getItems();

        List<PVResponse> result = new ArrayList<>();

        for (PersistentVolume pv : pvs) {
            String name = pv.getMetadata() != null ? pv.getMetadata().getName() : "";

            String capacity = "";
            if (pv.getSpec() != null
                    && pv.getSpec().getCapacity() != null
                    && pv.getSpec().getCapacity().get("storage") != null) {
                capacity = pv.getSpec().getCapacity().get("storage").toString();
            }

            String accessModes = "";
            if (pv.getSpec() != null && pv.getSpec().getAccessModes() != null) {
                accessModes = String.join(", ", pv.getSpec().getAccessModes());
            }

            String reclaimPolicy = pv.getSpec() != null
                    ? pv.getSpec().getPersistentVolumeReclaimPolicy()
                    : "";

            String status = pv.getStatus() != null
                    ? pv.getStatus().getPhase()
                    : "";

            String storageClass = pv.getSpec() != null
                    ? pv.getSpec().getStorageClassName()
                    : "";

            String claim = "";
            if (pv.getSpec() != null && pv.getSpec().getClaimRef() != null) {
                String ns = pv.getSpec().getClaimRef().getNamespace();
                String claimName = pv.getSpec().getClaimRef().getName();
                claim = (ns != null ? ns : "") + "/" + (claimName != null ? claimName : "");
            }

            String age = formatAge(
                    pv.getMetadata() != null ? pv.getMetadata().getCreationTimestamp() : null
            );

            result.add(new PVResponse(
                    name,
                    capacity,
                    accessModes,
                    reclaimPolicy,
                    status,
                    storageClass,
                    claim,
                    age
            ));
        }

        return result;
    }

    @Override
    public PersistentVolume create(PVRequest request) {
        validateRequest(request);

        List<String> accessModes = request.getAccessModes() != null && !request.getAccessModes().isEmpty()
                ? request.getAccessModes()
                : List.of("ReadWriteOnce");

        String reclaimPolicy = isBlank(request.getReclaimPolicy())
                ? "Retain"
                : request.getReclaimPolicy();

        String storageClassName = request.getStorageClassName() == null
                ? ""
                : request.getStorageClassName();

        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(request.getName());

        PersistentVolumeSpec spec = new PersistentVolumeSpec();
        spec.setCapacity(Map.of("storage", new Quantity(request.getCapacity())));
        spec.setAccessModes(accessModes);
        spec.setPersistentVolumeReclaimPolicy(reclaimPolicy);
        spec.setStorageClassName(storageClassName);

        String type = request.getType().trim();

        switch (type) {
            case "hostPath" -> {
                HostPathVolumeSource hostPathVolumeSource = new HostPathVolumeSource();
                hostPathVolumeSource.setPath(request.getHostPath());
                spec.setHostPath(hostPathVolumeSource);
            }

            case "nfs" -> {
                NFSVolumeSource nfsVolumeSource = new NFSVolumeSource();
                nfsVolumeSource.setServer(request.getNfsServer());
                nfsVolumeSource.setPath(request.getNfsPath());
                spec.setNfs(nfsVolumeSource);
            }

            case "local" -> {
                LocalVolumeSource localVolumeSource = new LocalVolumeSource();
                localVolumeSource.setPath(request.getLocalPath());
                spec.setLocal(localVolumeSource);
            }

            default -> throw new RuntimeException("PV type không hỗ trợ: " + type);
        }

        PersistentVolume pv = new PersistentVolume();
        pv.setApiVersion("v1");
        pv.setKind("PersistentVolume");
        pv.setMetadata(metadata);
        pv.setSpec(spec);

        return getKubernetesClient()
                .persistentVolumes()
                .resource(pv)
                .create();
    }

    @Override
    public boolean delete(String name) {
        List<StatusDetails> deleted = getKubernetesClient()
                .persistentVolumes()
                .withName(name)
                .delete();

        return deleted != null && !deleted.isEmpty();
    }

    @Override
    public void createFromFile(MultipartFile file) throws IOException {
        String yaml = new String(file.getBytes(), StandardCharsets.UTF_8);

        HasMetadata resource = Serialization.unmarshal(yaml);

        if (!(resource instanceof PersistentVolume pv)) {
            throw new RuntimeException("File không phải PersistentVolume");
        }

        if (pv.getMetadata() == null || isBlank(pv.getMetadata().getName())) {
            throw new RuntimeException("PV YAML thiếu metadata.name");
        }

        getKubernetesClient()
                .persistentVolumes()
                .resource(pv)
                .create();
    }

    @Override
    public String getRawYaml(String name) {
        PersistentVolume pv = getKubernetesClient()
                .persistentVolumes()
                .withName(name)
                .get();

        if (pv == null) {
            return null;
        }

        return Serialization.asYaml(pv);
    }

    private void validateRequest(PVRequest request) {
        if (request == null) {
            throw new RuntimeException("Request không được null");
        }

        if (isBlank(request.getName())) {
            throw new RuntimeException("Thiếu name");
        }

        if (isBlank(request.getCapacity())) {
            throw new RuntimeException("Thiếu capacity");
        }

        if (isBlank(request.getType())) {
            throw new RuntimeException("Thiếu type");
        }

        switch (request.getType().trim()) {
            case "hostPath" -> {
                if (isBlank(request.getHostPath())) {
                    throw new RuntimeException("hostPath không được để trống khi type = hostPath");
                }
            }

            case "nfs" -> {
                if (isBlank(request.getNfsServer())) {
                    throw new RuntimeException("nfsServer không được để trống khi type = nfs");
                }
                if (isBlank(request.getNfsPath())) {
                    throw new RuntimeException("nfsPath không được để trống khi type = nfs");
                }
            }

            case "local" -> {
                if (isBlank(request.getLocalPath())) {
                    throw new RuntimeException("localPath không được để trống khi type = local");
                }
            }

            default -> throw new RuntimeException("type chỉ hỗ trợ: hostPath, nfs, local");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String formatAge(String creationTimestamp) {
        if (creationTimestamp == null || creationTimestamp.isBlank()) {
            return "";
        }

        try {
            OffsetDateTime created = OffsetDateTime.parse(creationTimestamp);
            OffsetDateTime now = OffsetDateTime.now();

            long days = ChronoUnit.DAYS.between(created, now);
            if (days > 0) return days + "d";

            long hours = ChronoUnit.HOURS.between(created, now);
            if (hours > 0) return hours + "h";

            long minutes = ChronoUnit.MINUTES.between(created, now);
            if (minutes > 0) return minutes + "m";

            long seconds = ChronoUnit.SECONDS.between(created, now);
            return seconds + "s";
        } catch (DateTimeParseException e) {
            return creationTimestamp;
        }
    }
}