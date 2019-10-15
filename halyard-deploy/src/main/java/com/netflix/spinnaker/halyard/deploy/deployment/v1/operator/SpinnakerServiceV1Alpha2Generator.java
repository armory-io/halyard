/*
 * Copyright 2019, Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */
package com.netflix.spinnaker.halyard.deploy.deployment.v1.operator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SpinnakerServiceV1Alpha2Generator implements SpinnakerServiceGenerator {
  protected Yaml yamlParser = new Yaml();
  protected ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  @Override
  public String getAPIGroupVersion() {
    return "spinnaker.io/v1alpha2";
  }

  public String toManifest(CRConfig crConfig) {
    return yamlParser.dump(toMap(crConfig));
  }

  public Map<String, Object> toMap(CRConfig crConfig) {
    Map<String, Object> map = new HashMap<>();
    map.put("apiVersion", getAPIGroupVersion());
    map.put("kind", "SpinnakerService");
    map.put("metadata", getMetadata(crConfig));
    map.put("spec", getSpec(crConfig));
    return map;
  }

  protected Map<String, Object> getMetadata(CRConfig crConfig) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", crConfig.getName());
    return metadata;
  }

  protected Map<String, Object> getSpec(CRConfig crConfig) {
    Map<String, Object> spec = new HashMap<>();
    spec.put("spinnakerConfig", getSpinnakerConfig(crConfig));
    return spec;
  }

  protected Map<String, Object> getSpinnakerConfig(CRConfig crConfig) {
    Map<String, Object> config = new HashMap<>();
    config.put(
        "config", objectMapper.convertValue(crConfig.getDeploymentConfiguration(), Map.class));
    config.put("service-settings", crConfig.getServiceSettings());
    config.put("profiles", getProfiles(crConfig));
    config.put("files", crConfig.getRequiredFiles());
    return config;
  }

  protected Map<String, Object> getProfiles(CRConfig crConfig) {
    return crConfig.getProfiles().entrySet().stream()
        .collect(
            HashMap::new,
            (m, e) -> {
              if (e.getKey().equals(SpinnakerService.Type.DECK.getCanonicalName())) {
                Map<String, Object> deckProfile = new HashMap<>();
                deckProfile.put("content", e.getValue());
                m.put(e.getKey(), deckProfile);
              } else {
                Map<String, Object> map = yamlParser.load(e.getValue());
                if (map != null) {
                  m.put(e.getKey(), map);
                }
              }
            },
            HashMap::putAll);
  }
}
