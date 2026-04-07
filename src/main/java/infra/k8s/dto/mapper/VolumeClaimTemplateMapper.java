package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import org.springframework.stereotype.Component;
import infra.k8s.dto.statefulset.VolumeClaimTemplateDto;

import java.util.Collections;
import java.util.List;

@Component
public class VolumeClaimTemplateMapper {

    public PersistentVolumeClaim toPersistentVolumeClaim(VolumeClaimTemplateDto dto) {

        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(dto.getName())
                .endMetadata()
                .withNewSpec()
                .withStorageClassName(dto.getStorageClassName())
                .withAccessModes(dto.getAccessModes())
                .withNewResources()
                .addToRequests("storage", new Quantity(dto.getStorage()))
                .endResources()
                .endSpec()
                .build();
    }

    public List<PersistentVolumeClaim> toList(List<VolumeClaimTemplateDto> list) {

        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream()
                .map(this::toPersistentVolumeClaim)
                .toList();
    }
}