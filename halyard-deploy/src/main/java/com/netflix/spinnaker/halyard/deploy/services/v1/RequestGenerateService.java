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
package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.yaml.snakeyaml.Yaml;

public class RequestGenerateService {

  private static final String CONFIG_KEY = "config";
  private static final String SERVICE_SETTINGS_KEY = "serviceSettings";
  private static final String LOCAL_FILE_PREFIX = "files__";

  protected @Getter File baseDirectory;
  private Yaml yaml = new Yaml();
  private ObjectMapper objectMapper = new ObjectMapper();

  public RequestGenerateService() {
    this.baseDirectory = Files.createTempDir();
  }

  public void prepare(MultipartHttpServletRequest request) throws IOException {
    HalconfigDirectoryStructure.setDirectoryOverride(baseDirectory.getAbsolutePath());
    // Get the config first
    DeploymentConfiguration deploymentConfiguration =
        parseDeploymentConfiguration(request.getFile(CONFIG_KEY));
    // Write the main config
    writeHalConfig(deploymentConfiguration);
    // Write service settings
    writeServiceSettings(deploymentConfiguration, request);

    // Loop through all files
    request.getFileMap().entrySet().stream()
        .forEach(
            et -> {
              if (et.getKey().equals(CONFIG_KEY)) {
                return;
              }
              if (et.getKey().startsWith(LOCAL_FILE_PREFIX)) {
                // write all local files in hal config root
                writeFile("", et.getKey(), et.getValue());
              } else {
                String filePath = et.getKey().replaceAll("__", File.separator);
                writeFile(deploymentConfiguration.getName(), filePath, et.getValue());
              }
            });
  }

  public void cleanup() {
    HalconfigDirectoryStructure.setDirectoryOverride(null);
  }

  protected void writeHalConfig(DeploymentConfiguration deploymentConfiguration)
      throws IOException {
    Halconfig config = new Halconfig();
    config.getDeploymentConfigurations().clear();
    config.getDeploymentConfigurations().add(deploymentConfiguration);
    config.setCurrentDeployment(deploymentConfiguration.getName());

    File configFile = new File(baseDirectory, "config");
    FileOutputStream outputStream = new FileOutputStream(configFile);
    outputStream.write(yaml.dump(objectMapper.convertValue(config, Map.class)).getBytes());
  }

  protected void writeServiceSettings(
      DeploymentConfiguration deploymentConfiguration, MultipartHttpServletRequest request)
      throws IOException {
    MultipartFile file = request.getFile(SERVICE_SETTINGS_KEY);
    if (file != null && file.getSize() > 0) {
      // Read YAML and redispatch by service name (key), without checking service names
      Object obj = yaml.load(new ByteArrayInputStream(file.getBytes()));
      Map<String, Object> map = objectMapper.convertValue(obj, Map.class);
      map.entrySet().stream()
          .forEach(
              e -> {
                String filename =
                    new StringBuilder(deploymentConfiguration.getName())
                        .append(File.separator)
                        .append("service-settings")
                        .append(File.separator)
                        .append(e.getKey())
                        .append(".yml")
                        .toString();
                File targetFile = new File(baseDirectory, filename);
                targetFile.getParentFile().mkdirs();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
                  writer.write(yaml.dump(e.getValue()));
                } catch (IOException ex) {
                  throw new RuntimeException(ex);
                }
              });
    }
  }

  private void writeFile(String deploymentName, String filePath, MultipartFile fileContents) {
    Path target = Paths.get(baseDirectory.toString(), deploymentName, filePath).normalize();
    if (!target.startsWith(baseDirectory.toString())) {
      return;
    }

    File targetFile = target.toFile();
    targetFile.getParentFile().mkdirs();
    try {
      FileOutputStream outStream = new FileOutputStream(targetFile);
      outStream.write(fileContents.getBytes());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected DeploymentConfiguration parseDeploymentConfiguration(MultipartFile deploymentConfigFile)
      throws IOException {
    if (deploymentConfigFile == null) {
      throw new IllegalArgumentException("No deployment configuration file provided.");
    }
    Object obj = yaml.load(new ByteArrayInputStream(deploymentConfigFile.getBytes()));
    DeploymentConfiguration deploymentConfiguration =
        objectMapper.convertValue(obj, DeploymentConfiguration.class);
    if (deploymentConfiguration.getName() == null) {
      deploymentConfiguration.setName("default");
    }
    return deploymentConfiguration;
  }
}
