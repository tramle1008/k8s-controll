package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import infra.k8s.dto.common.MetadataDto;
import infra.k8s.dto.service.ServiceCreateRequest;
import infra.k8s.dto.service.ServicePortDto;
import infra.k8s.dto.service.ServiceService;
import infra.k8s.dto.service.ServiceSpecDto;
import infra.k8s.service.ClusterManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {
    private final ClusterManager clusterManager;

    @Override
    public String createService(ServiceCreateRequest request) {
        try {
            KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
            MetadataDto metadata = request.getMetadata();
            ServiceSpecDto spec = request.getSpec();

            String namespace = Optional.ofNullable(metadata.getNamespace())
                    .orElse("default");

            List<ServicePort> ports = spec.getPorts()
                    .stream()
                    .map(this::toServicePort)
                    .collect(Collectors.toList());

            ServiceSpecBuilder specBuilder = new ServiceSpecBuilder()
                    .withSelector(spec.getSelector())
                    .withPorts(ports);

            if (spec.getType() != null && !spec.getType().isBlank()) {
                specBuilder.withType(spec.getType());
            }

            if (spec.getClusterIP() != null && !spec.getClusterIP().isBlank()) {
                specBuilder.withClusterIP(spec.getClusterIP());
            }

            Service service = new ServiceBuilder()
                    .withNewMetadata()
                    .withName(metadata.getName())
                    .withNamespace(namespace)
                    .withLabels(metadata.getLabels())
                    .withAnnotations(metadata.getAnnotations())
                    .endMetadata()

                    .withSpec(specBuilder.build())

                    .build();

            kubernetesClient.services()
                    .inNamespace(namespace)
                    .resource(service)
                    .create();

            return "Service tao: " + metadata.getName();
        }catch (KubernetesClientException e) {
            String message = e.getStatus() != null
                        ? e.getStatus().getMessage()
                        : e.getMessage();

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        message
                );
        }
    }

    @Override
    public List<Map<String, Object>> listServices() {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();

        ServiceList list = kubernetesClient.services()
                .inAnyNamespace()
                .list();

        return list.getItems().stream().map(s -> {

            Map<String, Object> m = new HashMap<>();

            m.put("name", s.getMetadata().getName());
            m.put("namespace", s.getMetadata().getNamespace());
            m.put("type", s.getSpec().getType());
            m.put("clusterIP", s.getSpec().getClusterIP());
            m.put("ports", s.getSpec().getPorts());

            return m;

        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getServiceDetail(String namespace, String name) {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
        Service service = kubernetesClient.services()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (service == null) {
            throw new RuntimeException("Service not found");
        }

        Map<String, Object> result = new HashMap<>();

        result.put("name", service.getMetadata().getName());
        result.put("namespace", service.getMetadata().getNamespace());
        result.put("labels", service.getMetadata().getLabels());
        result.put("annotations", service.getMetadata().getAnnotations());
        result.put("type", service.getSpec().getType());
        result.put("selector", service.getSpec().getSelector());
        result.put("ports", service.getSpec().getPorts());
        result.put("clusterIP", service.getSpec().getClusterIP());

        return result;
    }

    @Override
    public String deleteService(String namespace, String name) {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
        boolean deleted = kubernetesClient.services()
                .inNamespace(namespace)
                .withName(name)
                .delete()
                .size() > 0;

        if (!deleted) {
            throw new RuntimeException("Service not found");
        }

        return "Service deleted: " + name;
    }

    @Override
    public String updateService(String namespace, String name, ServiceCreateRequest request) {
        try{
            KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
            Service existing = kubernetesClient.services()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existing == null) {
                throw new RuntimeException("Service not found");
            }

            List<ServicePort> ports = request.getSpec().getPorts()
                    .stream()
                    .map(this::toServicePort)
                    .collect(Collectors.toList());

            existing.getSpec().setPorts(ports);
            existing.getSpec().setSelector(request.getSpec().getSelector());
            existing.getSpec().setType(request.getSpec().getType());

            kubernetesClient.services()
                    .inNamespace(namespace)
                    .resource(existing)
                    .replace();

            return "Service updated: " + name;
        }catch (KubernetesClientException e) {

            String message = e.getStatus() != null
                    ? e.getStatus().getMessage()
                    : e.getMessage();

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    message
            );
        }

    }

    @Override
    public String createServiceFromYaml(MultipartFile file) throws IOException {
        KubernetesClient kubernetesClient = clusterManager.requireActiveClient();
        Service service = Serialization.unmarshal(
                file.getInputStream(),
                Service.class
        );

        kubernetesClient.services()
                .inNamespace(service.getMetadata().getNamespace())
                .resource(service)
                .create();

        return "Service created from YAML: " + service.getMetadata().getName();
    }


    private ServicePort toServicePort(ServicePortDto dto) {

        ServicePort port = new ServicePort();

        port.setName(dto.getName());
        port.setPort(dto.getPort());

        if (dto.getTargetPort() != null) {
            port.setTargetPort(new IntOrString(dto.getTargetPort()));
        }

        port.setProtocol(
                dto.getProtocol() == null ? "TCP" : dto.getProtocol()
        );

        if (dto.getNodePort() != null) {
            port.setNodePort(dto.getNodePort());
        }

        return port;
    }
}
