package infra.k8s.dto.hpa;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HpaDescribeDto {

    private String name;
    private String namespace;
    private String creationTimestamp;
    private String reference;

    private String metrics;

    private Integer minReplicas;
    private Integer maxReplicas;

    private String pods;

    private List<ConditionDto> conditions;

}