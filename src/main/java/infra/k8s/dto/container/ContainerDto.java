package infra.k8s.dto.container;

import lombok.Data;
import infra.k8s.dto.common.ResourceDto;

import java.util.List;

@Data
public class ContainerDto {
    private String name;
    private String image;
    private List<ContainerPortDto> ports;
    private ResourceDto resources;
    private List<EnvVarDto> env;
    private List<EnvFromDto> envFrom;
    private List<VolumeMountDto> volumeMounts;
}