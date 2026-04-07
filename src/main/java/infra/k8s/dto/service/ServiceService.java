package infra.k8s.dto.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ServiceService {
    String createService(ServiceCreateRequest request);

    List<Map<String, Object>> listServices();

     Map<String, Object> getServiceDetail(String namespace, String name);
    String deleteService(String namespace, String name);

    String updateService(String namespace, String name, ServiceCreateRequest request);

    String createServiceFromYaml(MultipartFile file) throws IOException;
}