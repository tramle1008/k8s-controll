package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.deployment.DeploymentDto;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.deployment.DeploymentCreateRequest;
import infra.k8s.dto.mapper.DeploymentMapper;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.DeploymentService;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {
    private final ClusterManager clusterManager;
    private final DeploymentMapper deploymentMapper;
    @Override
    public List<DeploymentDto> getAllDeployments() {
        KubernetesClient client = clusterManager.requireActiveClient();
        var deploymentList = client.apps()
                .deployments()
                .inAnyNamespace()
                .list();
        int count = deploymentList.getItems().size();
        log.info("Loaded {} deployments from cluster {}", count, clusterManager.getActiveClusterId());
        return deploymentList.getItems().stream()
                .map(DeploymentDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public String createDeployment(DeploymentCreateRequest request) {
        Deployment deployment = deploymentMapper.toDeployment(request);
        KubernetesClient client = clusterManager.requireActiveClient();
        client.apps()
                .deployments()
                .inNamespace(deployment.getMetadata().getNamespace())
                .resource(deployment)
                .create();
        return deployment.getMetadata().getName();
    }

    @Override
    public String createDeploymentFromYaml(MultipartFile yamlFile)  {
        if (yamlFile == null || yamlFile.isEmpty()) {
            throw new IllegalArgumentException("YAML file is required and must not be empty");
        }
        KubernetesClient client = clusterManager.requireActiveClient();
        // Đọc file thành InputStream
        try (InputStream inputStream = yamlFile.getInputStream()) {
            // Load Deployment từ YAML (Fabric8 hỗ trợ trực tiếp)
            Deployment deployment = client.apps()
                    .deployments()
                    .load(inputStream)
                    .item();  // .get() hoặc .item() tùy version, thường là .item()
            if (deployment == null) {
                throw new IllegalArgumentException("Invalid YAML: No Deployment resource found");
            }
            String fileNamespace = deployment.getMetadata().getNamespace();
            String finalNamespace = (fileNamespace != null && !fileNamespace.isEmpty())
                    ? fileNamespace
                    : "default";

            // Kiểm tra namespace tồn tại chưa
            boolean namespaceExists = client.namespaces().withName(finalNamespace).get() != null;
            if (!namespaceExists) {
                throw new IllegalArgumentException("Namespace " + finalNamespace +
                        " chưa tồn tại, vui lòng tạo trước");
            }

            // Nếu file có namespace, ưu tiên dùng cái trong file
            deployment.getMetadata().setNamespace(finalNamespace);

            // Optional: Override tên nếu cần, hoặc check tồn tại
            String deploymentName = deployment.getMetadata().getName();
            if (client.apps().deployments()
                    .inNamespace(finalNamespace)
                    .withName(deploymentName)
                    .get() != null) {
                throw new IllegalArgumentException("Deployment " + deploymentName + " đã tồn tại trong namespace " + finalNamespace);
            }

            // Tạo Deployment
            client.apps().deployments()
                    .inNamespace(finalNamespace)
                    .resource(deployment)
                    .create();

            log.info("Created Deployment from YAML: {} in namespace {}", deploymentName, finalNamespace);

            return deploymentName;
        } catch (Exception e) {
            log.error("Failed to create Deployment from YAML", e);
            throw new RuntimeException("YAML format không chuẩn: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDeploymentRawYaml(String namespace, String name) {

        KubernetesClient client = clusterManager.requireActiveClient();

        Deployment deployment = client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (deployment == null) {
            throw new RuntimeException(
                    "Deployment " + name + " not found in namespace " + namespace
            );
        }

        // Serialize raw Kubernetes object -> YAML
        return Serialization.asYaml(deployment);
    }

    @Override
    public List<DeploymentPodsDto> getDeploymentPods(String namespace, String deploymentName) {
        KubernetesClient client = clusterManager.requireActiveClient();
        Deployment deployment = client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .get();

        if (deployment == null) {
            throw new RuntimeException("Deployment not found");
        }

        Map<String, String> labels =
                deployment.getSpec().getSelector().getMatchLabels();

        List<Pod> podList = client.pods()
                .inNamespace(namespace)
                .withLabels(labels)
                .list()
                .getItems();

        return podList.stream()
                .map(this::toPodDto)
                .toList();
    }

    @Override
    public void scaleDeployment(String namespace, String name, Integer replicas) {

        KubernetesClient client = clusterManager.requireActiveClient();
        client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(name)
                .scale(replicas);
    }

    private DeploymentPodsDto toPodDto(Pod pod) {

        Integer restarts = pod.getStatus()
                .getContainerStatuses()
                .stream()
                .mapToInt(cs -> cs.getRestartCount())
                .sum();

        return new DeploymentPodsDto(
                pod.getMetadata().getName(),
                pod.getStatus().getPhase(),
                pod.getSpec().getNodeName(),
                restarts,
                pod.getStatus().getPodIP()
        );
    }

    public void restartDeployment(String namespace, String deploymentName) {
        KubernetesClient client = clusterManager.requireActiveClient();
        client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(deploymentName)
                .edit(deployment -> {

                    if (deployment == null) {
                        throw new RuntimeException("Deployment not found");
                    }

                    Map<String, String> annotations =
                            deployment.getSpec()
                                    .getTemplate()
                                    .getMetadata()
                                    .getAnnotations();

                    if (annotations == null) {
                        annotations = new HashMap<>();
                    }

                    annotations.put(
                            "kubectl.kubernetes.io/restartedAt",
                            Instant.now().toString()
                    );

                    deployment.getSpec()
                            .getTemplate()
                            .getMetadata()
                            .setAnnotations(annotations);

                    return deployment;
                });
    }
}