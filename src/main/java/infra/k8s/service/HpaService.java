package infra.k8s.service;

import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.hpa.HpaCreateRequest;
import infra.k8s.dto.hpa.HpaDescribeDto;
import infra.k8s.dto.hpa.HpaDto;

import java.util.List;

public interface HpaService {

    List<HpaDto> list();

    HorizontalPodAutoscaler get(String namespace, String name);

    ResponseEntity<String> create(HpaCreateRequest request);

    void delete(String namespace, String name);

    String createHpaFromYaml(MultipartFile yamlFile);

    @Nullable HpaDescribeDto describeHpa(String namespace, String name);
}