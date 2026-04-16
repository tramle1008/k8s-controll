package infra.k8s.dto.cluster;

import infra.k8s.Context.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private UserRole userRole;
    private String clusterName;
    public UserDto(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public UserDto(Long id, String username, UserRole userRole) {
        this.id = id;
        this.username = username;
        this.userRole = userRole;
    }
}