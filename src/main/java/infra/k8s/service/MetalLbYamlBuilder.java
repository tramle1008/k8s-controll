package infra.k8s.service;

public class MetalLbYamlBuilder {

    public static String build(String range) {
        return """
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: default
  namespace: metallb-system
spec:
  addresses:
    - %s
---
apiVersion: metallb.io/v1beta1
kind: L2Advertisement
metadata:
  name: default
  namespace: metallb-system
""".formatted(range);
    }
}