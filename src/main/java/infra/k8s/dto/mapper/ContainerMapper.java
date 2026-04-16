package infra.k8s.dto.mapper;

import infra.k8s.dto.container.*;
import io.fabric8.kubernetes.api.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ContainerMapper {

    private final ResourceMapper resourceMapper;

    public Container toContainer(ContainerDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Container name is required");
        }

        if (dto.getImage() == null || dto.getImage().isBlank()) {
            throw new IllegalArgumentException("Container image is required");
        }
        return new ContainerBuilder()
                .withName(dto.getName())
                .withImage(dto.getImage())
                .withPorts(Optional.ofNullable(mapPorts(dto.getPorts()))
                        .orElse(Collections.emptyList()))
                .withEnv(mapEnv(dto.getEnv()))
                .withEnvFrom(mapEnvFrom(dto.getEnvFrom()))
                .withVolumeMounts(mapVolumeMounts(dto.getVolumeMounts())) //toi moi them
                .withResources(resourceMapper.toResource(dto.getResources()))
                .build();
    }

    private List<ContainerPort> mapPorts(List<ContainerPortDto> ports) {

        if (ports == null || ports.isEmpty()) {
            return Collections.emptyList();
        }

        return ports.stream()
                .map(p -> new ContainerPortBuilder()
                        .withContainerPort(p.getContainerPort())
                        .withProtocol(
                                p.getProtocol() != null ? p.getProtocol() : "TCP"
                        )
                        .build())
                .toList();
    }

    private List<VolumeMount> mapVolumeMounts(List<VolumeMountDto> mounts) {

        if (mounts == null || mounts.isEmpty()) {
            return Collections.emptyList();
        }

        return mounts.stream()
                .map(m -> new VolumeMountBuilder()
                        .withName(m.getName())
                        .withMountPath(m.getMountPath())
                        .withReadOnly(m.getReadOnly() != null ? m.getReadOnly() : false)
                        .withSubPath(m.getSubPath())
                        .build())
                .toList();
    }

    private List<EnvVar> mapEnv(List<EnvVarDto> envList) {

        if (envList == null || envList.isEmpty()) {
            return Collections.emptyList();
        }

        return envList.stream()
                .map(e -> new EnvVarBuilder()
                        .withName(e.getName())
                        .withValue(e.getValue())
                        .build())
                .toList();
    }

    private List<EnvFromSource> mapEnvFrom(List<EnvFromDto> envFromList) {

        if (envFromList == null || envFromList.isEmpty()) {
            return Collections.emptyList();
        }

        return envFromList.stream()
                .map(e -> {

                    if (e == null || e.getType() == null || e.getName() == null) {
                        return null; //  chặn rỗng
                    }

                    if ("configMap".equals(e.getType())) {
                        return new EnvFromSourceBuilder()
                                .withConfigMapRef(
                                        new ConfigMapEnvSourceBuilder()
                                                .withName(e.getName())
                                                .build()
                                )
                                .build();
                    }

                    if ("secret".equals(e.getType())) {
                        return new EnvFromSourceBuilder()
                                .withSecretRef(
                                        new SecretEnvSourceBuilder()
                                                .withName(e.getName())
                                                .build()
                                )
                                .build();
                    }

                    return null; //  type không hợp lệ
                })
                .filter(Objects::nonNull) //  cực quan trọng
                .toList();
    }


}