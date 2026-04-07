package infra.k8s.dto.login;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private Long clusterId;
    private String errorMessage;
}