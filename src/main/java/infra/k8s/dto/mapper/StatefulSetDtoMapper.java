package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.springframework.stereotype.Component;
import infra.k8s.dto.statefulset.StatefulSetDto;

@Component
public class StatefulSetDtoMapper {

    public StatefulSetDto toDto(StatefulSet s) {

        StatefulSetDto dto = new StatefulSetDto();

        dto.setName(s.getMetadata().getName());
        dto.setNamespace(s.getMetadata().getNamespace());

        dto.setReplicas(
                s.getSpec() != null ? s.getSpec().getReplicas() : 0
        );

        dto.setReadyReplicas(
                s.getStatus() != null && s.getStatus().getReadyReplicas() != null
                        ? s.getStatus().getReadyReplicas()
                        : 0
        );

        dto.setServiceName(
                s.getSpec() != null ? s.getSpec().getServiceName() : null
        );

        dto.setCreationTimestamp(
                s.getMetadata().getCreationTimestamp()
        );

        return dto;
    }
}