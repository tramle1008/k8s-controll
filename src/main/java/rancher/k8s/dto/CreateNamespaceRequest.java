package rancher.k8s.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateNamespaceRequest {

    @NotBlank(message = "Namespace name is required")
    @Size(max = 63, message = "Namespace name must be <= 63 characters")
    @Pattern(
            regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$",
            message = "Invalid namespace name format"
    )
    private String name;
}
