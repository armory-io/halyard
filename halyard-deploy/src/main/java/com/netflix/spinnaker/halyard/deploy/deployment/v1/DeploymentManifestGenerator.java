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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Executor;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Service;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Utils;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
public class DeploymentManifestGenerator {

  @Autowired ServiceProviderFactory serviceProviderFactory;

  @Autowired VersionsService versionsService;

  @Autowired Yaml yamlParser;

  @Autowired GenerateService generateService;

  @Autowired KubernetesV2Utils kubernetesV2Utils;

  public String generateManifestList(DeploymentConfiguration deploymentConfiguration) {
    log.info("Parsing Halyard config");
    AccountDeploymentDetails<KubernetesAccount> deploymentDetails =
        getDeploymentDetails(deploymentConfiguration);
    KubectlServiceProvider serviceProvider =
        (KubectlServiceProvider) serviceProviderFactory.create(deploymentConfiguration);

    List<SpinnakerService.Type> serviceTypes =
        serviceProvider.getServices().stream()
            .map(SpinnakerService::getType)
            .collect(Collectors.toList());

    log.info("Resolving configuration");
    GenerateService.ResolvedConfiguration resolvedConfiguration =
        generateService.generateConfig("default", serviceTypes);

    log.info("Generating manifests for " + serviceTypes.size() + " services");
    Map<String, ManifestList> manifestListMap =
        buildManifestList(serviceProvider, deploymentDetails, resolvedConfiguration, serviceTypes);

    try {
      FileUtils.deleteDirectory(Paths.get(resolvedConfiguration.getStagingDirectory()).toFile());
    } catch (IOException e) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Problem.Severity.FATAL,
                  "Unable to clear old spinnaker config: " + e.getMessage() + ".")
              .build());
    }

    log.info("Generated manifests for " + manifestListMap.size() + " services");
    return manifestMapAsString(manifestListMap);
  }

  protected Map<String, ManifestList> buildManifestList(
      KubectlServiceProvider serviceProvider,
      AccountDeploymentDetails<KubernetesAccount> deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes) {
    List<KubernetesV2Service> services = serviceProvider.getServicesByPriority(serviceTypes);
    Map<String, ManifestList> manifestListMap = new HashMap<>();

    services.stream()
        .forEach(
            (service) -> {
              if (service instanceof SidecarService) {
                return;
              }

              ServiceSettings settings =
                  resolvedConfiguration.getServiceSettings((SpinnakerService) service);
              if (settings == null) {
                return;
              }

              if (settings.getEnabled() != null && !settings.getEnabled()) {
                return;
              }

              if (settings.getSkipLifeCycleManagement() != null
                  && settings.getSkipLifeCycleManagement()) {
                return;
              }
              ManifestList list = new ManifestList();
              KubernetesAccount account = deploymentDetails.getAccount();
              KubernetesV2Executor executor =
                  new KubernetesV2Executor(
                      DaemonTaskHandler.getJobExecutor(), account, kubernetesV2Utils) {
                    @Override
                    public void replace(String manifest) {
                      list.getResourceManifests().add(manifest);
                    }
                  };

              String serviceDefinition = service.getServiceYaml(resolvedConfiguration);
              list.setServiceManifest(serviceDefinition);

              String resourceDefinition =
                  service.getResourceYaml(executor, deploymentDetails, resolvedConfiguration);
              list.setDeploymentManifest(resourceDefinition);
              manifestListMap.put(service.getService().getCanonicalName(), list);
            });

    return manifestListMap;
  }

  protected String manifestMapAsString(Map<String, ManifestList> manifestListMap) {
    Map<String, Object> map =
        manifestListMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAsObject()));

    Map<String, Object> gMap = new HashMap<>();
    gMap.put("config", map);
    return yamlParser.dump(gMap);
  }

  protected AccountDeploymentDetails<KubernetesAccount> getDeploymentDetails(
      DeploymentConfiguration configuration) {
    BillOfMaterials billOfMaterials =
        versionsService.getBillOfMaterials(configuration.getVersion());
    AccountDeploymentDetails<KubernetesAccount> details = new AccountDeploymentDetails<>();
    details
        .setDeploymentConfiguration(configuration)
        .setDeploymentName(configuration.getName())
        .setBillOfMaterials(billOfMaterials);
    return details;
  }
}
