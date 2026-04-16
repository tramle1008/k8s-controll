package infra.k8s.service;

import infra.k8s.dto.cluster.AddonStatusDTO;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface AddonService {
    List<AddonStatusDTO> checkAddons();
//    void installLongHorn();
//    void installMetrics();
//    void installMetalLB();
//    void installIngressNginx();

    void installAddon(String name);
}
