package infra.k8s.dto.statefulset;

import infra.k8s.dto.container.EnvFromDto;
import lombok.Data;
import infra.k8s.dto.common.MetadataDto;
import infra.k8s.dto.container.ContainerDto;
import infra.k8s.dto.container.VolumeDto;

import java.util.List;

@Data
public class StatefulSetCreateRequest {
    private MetadataDto metadata;
    private Integer replicas;
    private String serviceName;
    private List<ContainerDto> containers;
    private List<EnvFromDto> envFrom;
    private List<VolumeClaimTemplateDto> volumeClaimTemplates;
    private List<VolumeDto> volumes;
}