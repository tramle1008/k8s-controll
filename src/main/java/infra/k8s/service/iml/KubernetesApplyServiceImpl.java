package infra.k8s.service.iml;

import infra.k8s.service.ClusterManager;
import infra.k8s.service.KubernetesApplyService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesApplyServiceImpl implements KubernetesApplyService {

    private final ClusterManager clusterManager;

    @Override
    public String applyYaml(MultipartFile file) {
        try {
            KubernetesClient client = clusterManager.getActiveClient();
            if (client == null) {
                throw new RuntimeException("No active Kubernetes cluster selected");
            }

            String yaml = new String(file.getBytes(), StandardCharsets.UTF_8);

            validateYamlSyntax(yaml);

            ByteArrayInputStream stream1 = new ByteArrayInputStream(yaml.getBytes());
            List<HasMetadata> resources = client.load(stream1).get();

            if (resources == null || resources.isEmpty()) {
                throw new RuntimeException("Không tìm thấy tài nguyên hợp lệ trong YAML");
            }

            log.info("Detected {} resources", resources.size());

            // ----------------------------------------------------------
            // DRY-RUN: server-side validation (like kubectl apply --dry-run=server)
            // ----------------------------------------------------------
            ByteArrayInputStream dryRunStream = new ByteArrayInputStream(yaml.getBytes());
            client.load(dryRunStream)
                    .dryRun()
                    .createOrReplace();

            // ----------------------------------------------------------
            //  APPLY thực
            // ----------------------------------------------------------
            ByteArrayInputStream applyStream = new ByteArrayInputStream(yaml.getBytes());
            client.load(applyStream)
                    .createOrReplace();

            return "Apply YAML thành công (" + resources.size() + " tài nguyên)";

        } catch (Exception e) {
            log.error("Apply YAML FAILED", e);
            return "Error: " + e.getMessage();
        }
    }

    private void validateYamlSyntax(String yaml) {
        Yaml validator = new Yaml();
        for (Object doc : validator.loadAll(yaml)) {
            if (doc == null) {
                throw new RuntimeException("YAML contains empty document");
            }
        }
    }
}