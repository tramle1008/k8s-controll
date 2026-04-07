package infra.k8s.dto.service;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ServiceSpecDto {
    private String type; // ClusterIP NodePort LoadBalancer
    private String clusterIP;
    private Map<String, String> selector;
    private List<ServicePortDto> ports;
}