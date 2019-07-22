package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.services.v1.RequestGenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.SidecarService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubectlServiceProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.yaml.snakeyaml.Yaml;

@Component
public class ManifestGenerator {

  @Autowired ServiceProviderFactory serviceProviderFactory;

  @Autowired Yaml yaml;

  @Autowired ObjectMapper objectMapper;

  RequestGenerateService generateService;

  @Autowired DeploymentService deploymentService;

  @Autowired List<SpinnakerService> spinnakerServices = new ArrayList<>();

  @Autowired ConfigParser configParser;

  @Autowired HalconfigParser halconfigParser;

  @Autowired VersionsService versionsService;

  @Autowired Yaml yamlParser;

  public String generateManifestList(MultipartHttpServletRequest request) {
    try {
      generateService =
          new RequestGenerateService(
              deploymentService, serviceProviderFactory, spinnakerServices, configParser);
      generateService.prepare(request);
      Halconfig halConfig = halconfigParser.getHalconfig();

      DeploymentConfiguration deploymentConfiguration =
          halConfig.getDeploymentConfigurations().get(0);
      AccountDeploymentDetails<KubernetesAccount> deploymentDetails =
          getDeploymentDetails(deploymentConfiguration);
      KubectlServiceProvider serviceProvider =
          (KubectlServiceProvider) serviceProviderFactory.create(deploymentConfiguration);

      List<SpinnakerService.Type> serviceTypes =
          serviceProvider.getServices().stream()
              .map(SpinnakerService::getType)
              .collect(Collectors.toList());

      GenerateService.ResolvedConfiguration resolvedConfiguration =
          generateService.generateConfig("default", serviceTypes);

      ManifestList manifestList =
          buildManifestList(
              serviceProvider, deploymentDetails, resolvedConfiguration, serviceTypes);

      return manifestListAsString(manifestList);
    } finally {
      generateService.cleanup();
    }
  }

  protected ManifestList buildManifestList(
      KubectlServiceProvider serviceProvider,
      AccountDeploymentDetails<KubernetesAccount> deploymentDetails,
      GenerateService.ResolvedConfiguration resolvedConfiguration,
      List<SpinnakerService.Type> serviceTypes) {
    List<KubernetesV2Service> services = serviceProvider.getServicesByPriority(serviceTypes);
    ManifestList list = new ManifestList();
    KubernetesManifestExecutor executor =
        new KubernetesManifestExecutor() {
          @Override
          public void replace(String manifest) {
            list.addManifest(manifest);
          }
        };

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

              String namespaceDefinition = service.getNamespaceYaml(resolvedConfiguration);
              list.addManifest(namespaceDefinition);

              String serviceDefinition = service.getServiceYaml(resolvedConfiguration);
              list.addManifest(serviceDefinition);

              String resourceDefinition =
                  service.getResourceYaml(executor, deploymentDetails, resolvedConfiguration);
              list.addManifest(resourceDefinition);
            });

    return list;
  }

  protected String manifestListAsString(ManifestList manifestList) {
    Map map = objectMapper.convertValue(manifestList, Map.class);
    return yamlParser.dump(map);
  }

  protected DeploymentConfiguration parseDeploymentConfiguration(InputStream inputStream) {
    return objectMapper.convertValue(yaml.load(inputStream), DeploymentConfiguration.class);
  }

  protected DeploymentConfiguration extractDeploymentConfiguration(
      MultipartHttpServletRequest request) throws IOException {
    List<MultipartFile> files = request.getFiles("config");
    if (files.size() != 1) {
      throw new IllegalArgumentException("Expected one file under config, found: " + files.size());
    }
    return parseDeploymentConfiguration(files.get(0).getInputStream());
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
