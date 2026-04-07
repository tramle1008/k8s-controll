package infra.k8s.dto.service;

import lombok.Data;

@Data
public class ServicePortDto {
    private String name; //http, https
    private Integer port;
    private Integer targetPort;
    private Integer nodePort;
    private String protocol;
}