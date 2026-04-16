package infra.k8s.service;

import infra.k8s.dto.ingress.IngressDTO;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface IngressService {
    @Nullable List<IngressDTO> listIngresses();

    void deleteIngress(String namespace, String name);
}
