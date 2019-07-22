package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

public class ManifestList {
  private String kind = "List";
  private String apiVersion = "1.0";
  private List<String> manifestStrings = new ArrayList<>();

  public void addManifest(String manifest) {
    manifestStrings.add(manifest);
  }

  public List<Map<String, Object>> getItems() {
    Yaml yaml = new Yaml();

    return manifestStrings.stream()
        .map(m -> yaml.<Map<String, Object>>load(m))
        .collect(Collectors.toList());
  }
}
