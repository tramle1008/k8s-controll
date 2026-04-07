package infra.k8s.dto.hpa;

import lombok.Data;
import infra.k8s.dto.common.MetadataDto;

@Data
public class HpaCreateRequest {

    private MetadataDto metadata;

    private HpaSpecDto spec;

}