package infra.k8s.dto.deployment;

import lombok.Data;
import infra.k8s.dto.container.ContainerDto;
import infra.k8s.dto.container.VolumeDto;

import java.util.List;
import java.util.Map;

@Data
public class DeploymentSpecDto {
    private Integer replicas;
    private Map<String, String> selector;
    private List<ContainerDto> containers;
    private List<VolumeDto> volumes;
}