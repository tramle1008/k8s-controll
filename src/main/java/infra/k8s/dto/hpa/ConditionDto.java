package infra.k8s.dto.hpa;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConditionDto {

    private String type;
    private String status;
    private String reason;
    private String message;

}