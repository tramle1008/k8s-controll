package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import infra.k8s.dto.common.MetadataDto;
import infra.k8s.dto.deployment.DeploymentCreateRequest;
import infra.k8s.dto.deployment.DeploymentSpecDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
//package rancher.k8s.dto.mapper;
@Component
@RequiredArgsConstructor
public class DeploymentMapper {

    private final ContainerMapper containerMapper;
    private final VolumeMapper volumeMapper;

    public Deployment toDeployment(DeploymentCreateRequest request) {

        MetadataDto metadata = request.getMetadata();
        DeploymentSpecDto spec = request.getSpec();

        Map<String,String> labels =
                Optional.ofNullable(metadata.getLabels())
                        .orElse(new HashMap<>());

        if (labels.isEmpty()) {
            labels.put("app", metadata.getName());
        }

        Map<String,String> selector =
                Optional.ofNullable(spec.getSelector())
                        .orElse(labels);

        Map<String,String> annotations =
                Optional.ofNullable(metadata.getAnnotations())
                        .orElse(new HashMap<>());

        Integer replicas =
                Optional.ofNullable(spec.getReplicas())
                        .orElse(1);

        if (spec.getContainers() == null || spec.getContainers().isEmpty()) {
            throw new IllegalArgumentException("Deployment phải có ít nhất 1 container");
        }

        List<Container> containers = spec.getContainers()
                .stream()
                .map(containerMapper::toContainer)
                .toList();
        String namespace =
                Optional.ofNullable(metadata.getNamespace())
                        .orElse("default");
        List<Volume> volumes = volumeMapper.toVolumes(spec.getVolumes());

        return new DeploymentBuilder()

                .withNewMetadata()
                .withName(metadata.getName())
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()

                .withNewSpec()
                .withReplicas(replicas)

                .withNewSelector()
                .addToMatchLabels(selector)
                .endSelector()

                .withNewTemplate()

                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()

                .withNewSpec()
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()

                .endTemplate()

                .endSpec()

                .build();
    }
}