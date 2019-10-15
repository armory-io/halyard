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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.operator.CRConfig;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.operator.SpinnakerServiceGenerator;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
public class DeploymentCRDGenerator {

  @Autowired KubectlServiceProvider kubectlServiceProvider;

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired List<SpinnakerServiceGenerator> generators;

  @Autowired Yaml yamlParser;

  @Autowired ObjectMapper objectMapper;

  public String generateCR(
      DeploymentConfiguration deploymentConfiguration, String serviceName, String apiGroupVersion) {
    log.info("Resolving configuration");
    CRConfig crConfig =
        new CRConfig().setDeploymentConfiguration(deploymentConfiguration).setName(serviceName);

    SpinnakerServiceGenerator generator = getGenerator(apiGroupVersion);

    // Runtime settings are needed when generating profiles and we generate profile to gather
    SpinnakerRuntimeSettings runtimeSettings =
        kubectlServiceProvider.buildRuntimeSettings(deploymentConfiguration);

    log.info("Collecting service settings per service");
    collectServiceSettings(runtimeSettings, crConfig);

    Path userProfilePath =
        halconfigDirectoryStructure.getUserProfilePath(deploymentConfiguration.getName());

    // Collect all existing profile files
    List<String> userProfileNames = aggregateProfilesInPath(userProfilePath.toString(), "");

    log.info("Collecting profile files per service");
    // For each enable service, collect main profiles and required files
    kubectlServiceProvider.getServices().stream()
        .filter(s -> runtimeSettings.getServiceSettings(s).getEnabled())
        .forEach(
            s -> collectProfiles(s, userProfilePath, runtimeSettings, userProfileNames, crConfig));

    // Gather required files and change their reference to the deployment configuration directory
    log.info("Make required files relative to deployment virtual directory");
    crConfig.stageRequiredFiles(halconfigDirectoryStructure.getRelativeFilesHome());

    log.info("Generating SpinnakerService manifest");
    return generator.toManifest(crConfig);
  }

  protected SpinnakerServiceGenerator getGenerator(String apiGroupVersion) {
    return generators.stream()
        .filter(
            g ->
                StringUtils.isEmpty(apiGroupVersion)
                    || g.getAPIGroupVersion().equals(apiGroupVersion))
        .findFirst()
        .orElseThrow(
            () ->
                new HalException(
                    Problem.Severity.ERROR, "apiVersion " + apiGroupVersion + " not recognized."));
  }

  protected void collectProfiles(
      SpinnakerService service,
      Path userProfilePath,
      SpinnakerRuntimeSettings runtimeSettings,
      List<String> userProfileNames,
      CRConfig crConfig) {
    userProfileNames.stream()
        .forEach(
            u -> {
              Optional<Profile> profile =
                  service.customProfile(
                      crConfig.getDeploymentConfiguration(),
                      runtimeSettings,
                      Paths.get(userProfilePath.toString(), service.getCanonicalName()),
                      u);
              if (profile.isPresent()) {
                try {
                  crConfig
                      .getProfiles()
                      .put(
                          service.getCanonicalName(),
                          new String(Files.readAllBytes(Paths.get(userProfilePath.toString(), u))));
                } catch (IOException e) {
                  throw new HalException(Problem.Severity.ERROR, "Unable to read file " + u, e);
                }
              }
            });
  }

  protected void collectServiceSettings(
      SpinnakerRuntimeSettings runtimeSettings, CRConfig crConfig) {
    kubectlServiceProvider.getServices().stream()
        .filter(s -> runtimeSettings.getServiceSettings(s.getType()).getEnabled())
        .forEach(
            s -> {
              File userSettingsFile =
                  new File(
                      halconfigDirectoryStructure
                          .getUserServiceSettingsPath(
                              crConfig.getDeploymentConfiguration().getName())
                          .toString(),
                      s.getCanonicalName() + ".yml");
              if (userSettingsFile.exists() && userSettingsFile.length() != 0) {
                try (FileInputStream is = new FileInputStream(userSettingsFile)) {
                  crConfig
                      .getServiceSettings()
                      .put(
                          s.getCanonicalName(),
                          objectMapper.convertValue(yamlParser.load(is), Map.class));
                } catch (IOException e) {
                  throw new HalException(
                      Problem.Severity.FATAL,
                      "Unable to read provided user settings: " + e.getMessage(),
                      e);
                }
              }
            });
  }

  private static List<String> aggregateProfilesInPath(String basePath, String relativePath) {
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
                return Collections.singletonList(filePrefix + f.getName());
              }
              return aggregateProfilesInPath(basePath, filePrefix + f.getName());
            })
        .reduce(
            new ArrayList<>(),
            (a, b) -> {
              a.addAll(b);
              return a;
            });
  }
}
