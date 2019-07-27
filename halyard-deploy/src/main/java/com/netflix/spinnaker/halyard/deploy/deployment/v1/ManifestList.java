package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.yaml.snakeyaml.Yaml;

@Data
public class ManifestList {
  private String deploymentManifest;
  private String serviceManifest;
  private List<String> resourceManifests = new ArrayList<>();

  public Map<String, Object> getAsObject() {
    Yaml yaml = new Yaml();
    Map<String, Object> map = new HashMap<>();

    if (deploymentManifest != null) {
      map.put("deployment", yaml.load(deploymentManifest));
    }
    if (serviceManifest != null) {
      map.put("service", yaml.load(serviceManifest));
    }
    map.put(
        "resources",
        resourceManifests.stream().map(m -> yaml.load(m)).collect(Collectors.toList()));
    return map;
  }
}
