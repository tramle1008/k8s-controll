package infra.k8s.dto.container;

import lombok.Data;

@Data
public class ContainerPortDto {
    private Integer containerPort;
    private String protocol; // TCP / UDP
}