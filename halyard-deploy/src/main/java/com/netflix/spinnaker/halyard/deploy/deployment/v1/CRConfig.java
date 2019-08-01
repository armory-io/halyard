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
package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.yaml.snakeyaml.Yaml;

@Data
public class CRConfig {
  private Map<String, File> serviceSettings = new HashMap<>();
  private DeploymentConfiguration config;
  private List<File> profileFiles = new ArrayList<>();
  private List<String> requiredFiles = new ArrayList<>();

  public Map<String, Object> toConfigMap(ObjectMapper objectMapper, Yaml yamlParser) {
    Map<String, Object> map = new HashMap<>();
    map.put("config", objectMapper.convertValue(config, Map.class));
    map.put("service-settings", getServiceSettingsMap(yamlParser));
    profileFiles.stream().forEach(f -> map.put("profiles__" + f.getName(), readContent(f)));
    return map;
  }

  protected String readContent(File f) {
    try {
      return new String(Files.readAllBytes(Paths.get(f.toURI())));
    } catch (IOException e) {
      throw new HalException(Problem.Severity.ERROR, "Unable to read file " + f.getName(), e);
    }
  }

  protected Object getServiceSettingObject(Yaml yamlParser, File file) {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      return yamlParser.load(reader);
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.ERROR, "Unable to read service settings " + file.getName(), e);
    }
  }

  protected Map<String, Object> getServiceSettingsMap(Yaml yamlParser) {
    return serviceSettings.entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey(), e -> getServiceSettingObject(yamlParser, e.getValue())));
  }
}
