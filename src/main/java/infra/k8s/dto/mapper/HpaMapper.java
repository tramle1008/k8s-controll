package infra.k8s.dto.mapper;


import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder;
import infra.k8s.dto.hpa.HpaCreateRequest;
import infra.k8s.dto.hpa.HpaSpecDto;

import java.util.ArrayList;
import java.util.List;
public class HpaMapper {

    public static HorizontalPodAutoscaler toK8s(HpaCreateRequest req) {

        HpaSpecDto spec = req.getSpec();

        List<MetricSpec> metrics = new ArrayList<>();

        // CPU metric
        if (spec.getCpuUtilization() != null) {

            metrics.add(

                    new MetricSpecBuilder()

                            .withType("Resource")

                            .withNewResource()

                            .withName("cpu")

                            .withNewTarget()
                            .withType("Utilization")
                            .withAverageUtilization(spec.getCpuUtilization())
                            .endTarget()

                            .endResource()

                            .build()
            );
        }

        // MEMORY metric
        if (spec.getMemoryUtilization() != null) {

            metrics.add(

                    new MetricSpecBuilder()

                            .withType("Resource")

                            .withNewResource()

                            .withName("memory")

                            .withNewTarget()
                            .withType("Utilization")
                            .withAverageUtilization(spec.getMemoryUtilization())
                            .endTarget()

                            .endResource()

                            .build()
            );
        }

        return new HorizontalPodAutoscalerBuilder()

                .withNewMetadata()
                .withName(req.getMetadata().getName())
                .withNamespace(req.getMetadata().getNamespace())
                .endMetadata()

                .withNewSpec()

                .withMinReplicas(spec.getMinReplicas())
                .withMaxReplicas(spec.getMaxReplicas())

                .withNewScaleTargetRef()
                .withApiVersion("apps/v1")
                .withKind(spec.getTargetKind())
                .withName(spec.getTargetName())
                .endScaleTargetRef()

                .withMetrics(metrics)

                .endSpec()

                .build();
    }
}