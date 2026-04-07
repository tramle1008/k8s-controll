package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.springframework.stereotype.Component;
import infra.k8s.dto.container.VolumeDto;

import java.util.Collections;
import java.util.List;

@Component
public class VolumeMapper {

    public List<Volume> toVolumes(List<VolumeDto> volumes) {

        if (volumes == null || volumes.isEmpty()) {
            return Collections.emptyList();
        }

        return volumes.stream()
                .map(this::toVolume)
                .toList();
    }

    private Volume toVolume(VolumeDto dto) {

        VolumeBuilder builder = new VolumeBuilder()
                .withName(dto.getName());

        if (dto.getType() == null) {
            return builder.build();
        }

        switch (dto.getType()) {

            case "configMap":
                builder.withNewConfigMap()
                        .withName(dto.getConfigMapName())
                        .endConfigMap();
                break;

            case "secret":
                builder.withNewSecret()
                        .withSecretName(dto.getSecretName())
                        .endSecret();
                break;

            case "pvc":
                builder.withNewPersistentVolumeClaim()
                        .withClaimName(dto.getPvcName())
                        .endPersistentVolumeClaim();
                break;

            case "emptyDir":
                builder.withNewEmptyDir().endEmptyDir();
                break;
        }

        return builder.build();
    }
}