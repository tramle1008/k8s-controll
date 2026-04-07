package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import infra.k8s.dto.configmap.ConfigMapCreateRequest;
import infra.k8s.dto.configmap.ConfigMapDto;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.ConfigMapService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigMapServiceImpl implements ConfigMapService {

    private final ClusterManager clusterManager;

    @Override
    public List<ConfigMapDto> getAll() {

        KubernetesClient client = clusterManager.requireActiveClient();

        return client.configMaps()
                .inAnyNamespace()
                .list()
                .getItems()
                .stream()
                .map(cm -> {

                    int dataCount = cm.getData() == null ? 0 : cm.getData().size();

                    String age = AgeUtils.formatAge(
                            cm.getMetadata().getCreationTimestamp()
                    );

                    return new ConfigMapDto(
                            cm.getMetadata().getName(),
                            cm.getMetadata().getNamespace(),
                            dataCount,
                            age
                    );
                })
                .toList();
    }
    public class AgeUtils {

        public static String formatAge(String timestamp) {

            Instant created = Instant.parse(timestamp);

            Duration duration = Duration.between(created, Instant.now());

            long minutes = duration.toMinutes();

            if (minutes < 60)
                return minutes + "m";

            long hours = duration.toHours();

            if (hours < 24)
                return hours + "h";

            long days = duration.toDays();

            return days + "d";
        }
    }
    @Override
    public void create(ConfigMapCreateRequest dto) {

        KubernetesClient client = clusterManager.requireActiveClient();

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .withNamespace(dto.getNamespace())
                .endMetadata()
                .withData(dto.getData())
                .build();

        client.configMaps()
                .inNamespace(dto.getNamespace())
                .resource(configMap)
                .create();
    }



    @Override
    public void createFromFile(String namespace, MultipartFile file) throws IOException {

        KubernetesClient client = clusterManager.requireActiveClient();

        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new RuntimeException("File name invalid");
        }

        String content = new String(file.getBytes());

        Map<String, String> data = new HashMap<>();
        data.put(fileName, content);

        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(fileName.replace(".", "-"))
                .withNamespace(namespace)
                .endMetadata()
                .withData(data)
                .build();

        client.configMaps()
                .inNamespace(namespace)
                .resource(configMap)
                .create();
    }

    @Override
    public void delete(String namespace, String name) {

        KubernetesClient client = clusterManager.requireActiveClient();

        List<StatusDetails> deleted = client.configMaps()
                .inNamespace(namespace)
                .withName(name)
                .delete();

        if (deleted == null || deleted.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "ConfigMap not found: " + name
            );
        }
    }
}