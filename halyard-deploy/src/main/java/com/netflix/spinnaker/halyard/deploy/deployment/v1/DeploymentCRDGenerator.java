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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

  public String generateCR(DeploymentConfiguration deploymentConfiguration) {
    log.info("Parsing Halyard config");
    KubectlServiceProvider serviceProvider =
        (KubectlServiceProvider) serviceProviderFactory.create(deploymentConfiguration);

    log.info("Resolving configuration");
    CRConfig crConfig = new CRConfig();
    crConfig.setConfig(deploymentConfiguration);

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
    return getConfigMap(crConfig);
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

  protected String getConfigMap(CRConfig crConfigMap) {
    return yamlParser.dump(crConfigMap.toConfigMap(objectMapper, yamlParser));
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
}
