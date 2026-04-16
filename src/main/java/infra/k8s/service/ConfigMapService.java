package infra.k8s.service;

import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.configmap.ConfigMapCreateRequest;
import infra.k8s.dto.configmap.ConfigMapDto;

import java.io.IOException;
import java.util.List;

public interface ConfigMapService {

    List<ConfigMapDto> getAll();

    void create(ConfigMapCreateRequest dto);

    void createFromFile(String namespace, String type, MultipartFile file) throws IOException;

    void delete(String namespace, String name);

    List<ConfigMapDto> getByNameSpace(String namespace);
}