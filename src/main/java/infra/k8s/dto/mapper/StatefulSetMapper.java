package infra.k8s.dto.mapper;


import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import infra.k8s.dto.container.VolumeDto;
import infra.k8s.dto.statefulset.StatefulSetCreateRequest;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatefulSetMapper {

    private final ContainerMapper containerMapper;
    private final VolumeClaimTemplateMapper pvcMapper;
    public StatefulSet toStatefulSet(StatefulSetCreateRequest request) {
        List<Container> containers = request.getContainers()
                .stream()
                .map(containerMapper::toContainer)
                .toList();

        List<PersistentVolumeClaim> pvcTemplates = pvcMapper.toList(request.getVolumeClaimTemplates());
        List<Volume> volumes = mapVolumes(request.getVolumes());

        return new StatefulSetBuilder()
                .withNewMetadata()
                .withName(request.getMetadata().getName())
                .withNamespace(request.getMetadata().getNamespace())
                .endMetadata()
                .withNewSpec()
                .withServiceName(request.getServiceName())
                .withReplicas(request.getReplicas())
                .withNewSelector()
                .addToMatchLabels("app", request.getMetadata().getLabels().get("app"))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", request.getMetadata().getLabels().get("app"))
                .endMetadata()
                .withNewSpec()
                .withContainers(containers)
                .withVolumes(volumes) // <-- thêm volumes vào đây
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(pvcTemplates)
                .endSpec()
                .build();
    }

    private List<Volume> mapVolumes(List<VolumeDto> volumes) {
        if (volumes == null || volumes.isEmpty()) {
            return Collections.emptyList();
        }

        return volumes.stream().map(v -> {
            VolumeBuilder builder = new VolumeBuilder().withName(v.getName());

            switch (v.getType()) {
                case "configMap" -> builder.withConfigMap(
                        new ConfigMapVolumeSourceBuilder().withName(v.getConfigMapName()).build()
                );
                case "secret" -> builder.withSecret(
                        new SecretVolumeSourceBuilder().withSecretName(v.getSecretName()).build()
                );
                case "emptyDir" -> builder.withEmptyDir(new EmptyDirVolumeSourceBuilder().build());
                case "pvc" -> builder.withPersistentVolumeClaim(
                        new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(v.getPvcName()).build()
                );
            }

            return builder.build();
        }).toList();
    }
}