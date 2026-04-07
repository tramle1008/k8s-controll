package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import infra.k8s.dto.container.ContainerDto;
import infra.k8s.dto.container.ContainerPortDto;
import infra.k8s.dto.container.EnvVarDto;
import infra.k8s.dto.container.VolumeMountDto;

import java.util.Collections;
import java.util.List;
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
//them
    private List<VolumeMount> mapVolumeMounts(List<VolumeMountDto> mounts) {

        if (mounts == null || mounts.isEmpty()) {
            return Collections.emptyList();
        }

        return mounts.stream()
                .map(m -> new VolumeMountBuilder()
                        .withName(m.getName())
                        .withMountPath(m.getMountPath())
                        .withReadOnly(m.getReadOnly() != null ? m.getReadOnly() : false)
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
}