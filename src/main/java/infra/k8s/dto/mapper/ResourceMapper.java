package infra.k8s.dto.mapper;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import org.springframework.stereotype.Component;
import infra.k8s.dto.common.ResourceDto;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResourceMapper {

    public ResourceRequirements toResource(ResourceDto dto) {

        if (dto == null) {
            return null;
        }

        Map<String, Quantity> limits = map(dto.getLimits());
        Map<String, Quantity> requests = map(dto.getRequests());

        return new ResourceRequirementsBuilder()
                .withLimits(limits)
                .withRequests(requests)
                .build();
    }

    private Map<String, Quantity> map(Map<String,String> source){

        if(source == null){
            return Collections.emptyMap();
        }

        return source.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new Quantity(e.getValue())
                ));
    }
}