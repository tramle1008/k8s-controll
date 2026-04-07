package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.secret.SecretRequest;
import infra.k8s.dto.secret.SecretResponse;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.SecretService;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SecretServiceIml implements SecretService {

    private final ClusterManager clusterManager;



    public List<SecretResponse> getAll() {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();

        List<Secret> secrets = kubernetesClient.secrets().inAnyNamespace().list().getItems();
        List<SecretResponse> result = new ArrayList<>();

        for (Secret secret : secrets) {
            int dataCount = secret.getData() != null ? secret.getData().size() : 0;

            result.add(new SecretResponse(
                    secret.getMetadata().getName(),
                    secret.getMetadata().getNamespace(),
                    secret.getType(),
                    dataCount,
                    formatAge(secret.getMetadata().getCreationTimestamp())
            ));
        }

        return result;
    }

    public Secret create(SecretRequest request) {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();

        String namespace = request.getNamespace() == null || request.getNamespace().isBlank()
                ? "default"
                : request.getNamespace();

        String type = request.getType() == null || request.getType().isBlank()
                ? "Opaque"
                : request.getType();

        SecretBuilder builder = new SecretBuilder()
                .withNewMetadata()
                .withName(request.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withType(type);

        if (request.getData() != null && !request.getData().isEmpty()) {
            builder.withStringData(request.getData());
        }

        Secret secret = builder.build();

        return kubernetesClient.secrets()
                .inNamespace(namespace)
                .resource(secret)
                .create();
    }

    public boolean delete(String namespace, String name) {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();


        return kubernetesClient.secrets()
                .inNamespace(namespace)
                .withName(name)
                .delete()
                .stream()
                .findFirst()
                .isPresent();
    }

    public void createFromFile(MultipartFile file) throws IOException {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();

        String yaml = new String(file.getBytes(), StandardCharsets.UTF_8);

        HasMetadata resource = Serialization.unmarshal(yaml);

        if (!(resource instanceof Secret)) {
            throw new IllegalArgumentException("File không phải Kubernetes Secret");
        }

        Secret secret = (Secret) resource;

        if (secret.getMetadata() == null || secret.getMetadata().getName() == null) {
            throw new IllegalArgumentException("Secret YAML thiếu metadata.name");
        }

        if (secret.getMetadata().getNamespace() == null || secret.getMetadata().getNamespace().isBlank()) {
            secret.getMetadata().setNamespace("default");
        }

        kubernetesClient.secrets()
                .inNamespace(secret.getMetadata().getNamespace())
                .resource(secret)
                .create();
    }

    @Override
    public String getRawYaml(String namespace, String name) {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();

        Secret secret = kubernetesClient.secrets()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (secret == null) {
            return null;
        }
        return Serialization.asYaml(secret);
    }

    private String formatAge(String creationTimestamp) {
        if (creationTimestamp == null || creationTimestamp.isBlank()) {
            return "-";
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
        } catch (DateTimeParseException e) {
            return "-";
        }
    }
}
