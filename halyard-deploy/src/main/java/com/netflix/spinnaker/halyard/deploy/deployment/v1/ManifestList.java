package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.core.resource.v1.JinjaJarResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

public class ManifestList {
  private List<String> manifestStrings = new ArrayList<>();

  public void addManifest(String manifest) {
    manifestStrings.add(manifest);
  }

  public String asListKind() {
    Yaml yaml = new Yaml();

    List<Map<String, Object>> items =
        manifestStrings.stream()
            .map(m -> yaml.<Map<String, Object>>load(m))
            .collect(Collectors.toList());

    return new JinjaJarResource("/kubernetes/manifests/list.yml")
        .addBinding("items", items)
        .toString();
  }
}
