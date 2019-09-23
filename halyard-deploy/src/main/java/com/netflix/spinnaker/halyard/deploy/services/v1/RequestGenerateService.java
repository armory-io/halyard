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
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
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

  protected @Getter File baseDirectory;
  protected Yaml yaml = new Yaml();
  protected ObjectMapper objectMapper = new ObjectMapper();

  public RequestGenerateService() {
    this.baseDirectory = Files.createTempDir();
  }

  public void prepare(MultipartHttpServletRequest request) throws IOException {
    // Get the config first
    DeploymentConfiguration deploymentConfiguration =
        parseDeploymentConfiguration(request.getFile(CONFIG_KEY));

    // Overwrite hal config directory structure
    HalconfigDirectoryStructure.setDirectoryOverride(baseDirectory.getAbsolutePath());
    HalconfigDirectoryStructure.setRelativeFilesHome(
        baseDirectory.getAbsolutePath() + File.separator + deploymentConfiguration.getName());

    // Write the main config
    writeHalConfig(deploymentConfiguration);

    // Write everything else
    request.getFileMap().entrySet().stream()
        .forEach(
            et -> {
              if (et.getKey().equals(CONFIG_KEY)) {
                return;
              }
              writeFile(deploymentConfiguration.getName(), et.getKey(), et.getValue());
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

  private void writeFile(String deploymentName, String filePath, MultipartFile fileContents) {
    String newPath = filePath.replaceAll("__", File.separator);
    Path target = Paths.get(baseDirectory.toString(), deploymentName, newPath).normalize();
    if (!target.startsWith(baseDirectory.toString())) {
      throw new HalException(
          Problem.Severity.ERROR,
          "File path "
              + filePath
              + " must not resolve to a dir outside of "
              + baseDirectory.toString());
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
