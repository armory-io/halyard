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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.StrictObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
public class DeploymentCRDGenerator {

  @Autowired ServiceProviderFactory serviceProviderFactory;

  @Autowired Yaml yamlParser;

  @Autowired StrictObjectMapper objectMapper;

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  public String generateCR(
      DeploymentConfiguration deploymentConfiguration, String storeType, String storeName) {
    log.info("Parsing Halyard config");
    KubectlServiceProvider serviceProvider =
        (KubectlServiceProvider) serviceProviderFactory.create(deploymentConfiguration);

    log.info("Resolving configuration");
    CRConfig crConfig = new CRConfig();

    // Runtime settings are needed when generating profiles and we generate profile to gather
    SpinnakerRuntimeSettings runtimeSettings =
        serviceProvider.buildRuntimeSettings(deploymentConfiguration);

    // Gather all the profile file names
    Path userProfilePath =
        halconfigDirectoryStructure.getUserProfilePath(deploymentConfiguration.getName());
    List<String> userProfileNames =
        aggregateProfilesInPath(userProfilePath.toString(), "", crConfig);

    log.info("Collecting required files per service");
    serviceProvider.getServices().stream()
        .forEach(
            s ->
                getProfileFiles(
                    s,
                    userProfilePath,
                    deploymentConfiguration,
                    runtimeSettings,
                    userProfileNames,
                    crConfig));
    log.info("Generating CR config map");
    return getConfigMap(crConfig, deploymentConfiguration, storeType, storeName);
  }

  protected void getServiceSettingsFiles(
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerService service,
      CRConfig crConfig) {
    File userSettingsFile =
        new File(
            halconfigDirectoryStructure
                .getUserServiceSettingsPath(deploymentConfiguration.getName())
                .toString(),
            service.getCanonicalName() + ".yml");

    if (userSettingsFile.exists() && userSettingsFile.length() != 0) {
      crConfig.getServiceSettings().put(service.getCanonicalName(), userSettingsFile);
    }
  }

  protected void getProfileFiles(
      SpinnakerService service,
      Path userProfilePath,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings runtimeSettings,
      List<String> userProfileNames,
      CRConfig crConfig) {
    userProfileNames.stream()
        .map(
            s ->
                (Optional<Profile>)
                    service.customProfile(
                        deploymentConfiguration,
                        runtimeSettings,
                        Paths.get(userProfilePath.toString(), s),
                        s))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(p -> crConfig.getRequiredFiles().addAll(p.getRequiredFiles()));
    getServiceSettingsFiles(deploymentConfiguration, service, crConfig);
  }

  protected String getConfigMap(
      CRConfig crConfigMap,
      DeploymentConfiguration deploymentConfiguration,
      String storeType,
      String storeName) {
    return yamlParser.dump(getCRStore(crConfigMap, deploymentConfiguration, storeName));
  }

  private static List<String> aggregateProfilesInPath(
      String basePath, String relativePath, CRConfig crConfigMap) {
    String filePrefix;
    if (!relativePath.isEmpty()) {
      filePrefix = relativePath + File.separator;
    } else {
      filePrefix = relativePath;
    }

    File currentPath = new File(basePath, relativePath);
    return Arrays.stream(currentPath.listFiles())
        .map(
            f -> {
              if (f.isFile()) {
                crConfigMap.getProfileFiles().add(f);
                return Collections.singletonList(filePrefix + f.getName());
              }
              return aggregateProfilesInPath(basePath, filePrefix + f.getName(), crConfigMap);
            })
        .reduce(
            new ArrayList<>(),
            (a, b) -> {
              a.addAll(b);
              return a;
            });
  }

  public Map<String, Object> getCRStore(
      CRConfig crConfig, DeploymentConfiguration deploymentConfiguration, String storeName) {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> data = new HashMap<>();
    Map<String, Object> metadata = new HashMap<>();
    map.put("apiVersion", "v1");
    map.put("kind", "ConfigMap");
    map.put("metadata", metadata);
    map.put("data", data);

    metadata.put("name", storeName);
    data.put(
        "config", yamlParser.dump(objectMapper.convertValue(deploymentConfiguration, Map.class)));
    data.put(
        "service-settings",
        yamlParser.dump(
            crConfig.getServiceSettings().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        e -> e.getKey(), e -> getServiceSettingObject(e.getValue())))));
    crConfig.getProfileFiles().stream()
        .forEach(f -> data.put("profiles__" + f.getName(), readContent(f)));
    return map;
  }

  protected String readContent(File file) {
    try {
      return new String(Files.readAllBytes(Paths.get(file.toURI())));
    } catch (IOException e) {
      throw new HalException(Problem.Severity.ERROR, "Unable to read file " + file.getName(), e);
    }
  }

  protected Object getServiceSettingObject(File file) {
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      return yamlParser.load(reader);
    } catch (IOException e) {
      throw new HalException(
          Problem.Severity.ERROR, "Unable to read service settings " + file.getName(), e);
    }
  }
}
