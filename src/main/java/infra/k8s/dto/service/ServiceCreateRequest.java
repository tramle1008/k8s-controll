package infra.k8s.dto.service;

import lombok.Data;
import infra.k8s.dto.common.MetadataDto;

@Data
public class ServiceCreateRequest {
    private MetadataDto metadata;
    private ServiceSpecDto spec;
}