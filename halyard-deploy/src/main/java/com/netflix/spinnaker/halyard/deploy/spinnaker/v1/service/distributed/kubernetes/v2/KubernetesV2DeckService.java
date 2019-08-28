/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Plugins;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Manifest;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.DeckDockerProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ConfigSource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.DistributedService.DeployPriority;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.KubernetesSharedServiceSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Component
@EqualsAndHashCode(callSuper = true)
public class KubernetesV2DeckService extends DeckService
    implements KubernetesV2Service<DeckService.Deck> {
  final DeployPriority deployPriority = new DeployPriority(0);

  @Delegate @Autowired KubernetesV2ServiceDelegate serviceDelegate;

  @Autowired DeckDockerProfileFactory deckDockerProfileFactory;

  private final String settingsPath = "/opt/spinnaker/config";
  private final String settingsJs = "settings.js";
  private final String settingsJsLocal = "settings-local.js";

  @Override
  public ServiceSettings defaultServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings(deploymentConfiguration.getSecurity().getUiSecurity());
  }

  @Override
  public boolean runsOnJvm() {
    return false;
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals(settingsJs) || profileName.equals(settingsJsLocal)) {
      return Optional.of(Paths.get(settingsPath, profileName).toString());
    } else {
      return Optional.empty();
    }
  }

  // TODO how to get plugins here
  @Override
  public List<String> getInitContainers(AccountDeploymentDetails<KubernetesAccount> details) {
    Plugins plugins = details.getDeploymentConfiguration().getPlugins();
    if (plugins.isEnabled() && plugins.isDownloadingEnabled()) {
      // Create an initContainer w/ the volume you expect
      Map pluginDownloadingContainer = getPluginDownloadingContainer(plugins);

      List<Map> initContainers =
          details
              .getDeploymentConfiguration()
              .getDeploymentEnvironment()
              .getInitContainers()
              .getOrDefault(getServiceName(), new ArrayList<>());

      initContainers.add(pluginDownloadingContainer);
      details
          .getDeploymentConfiguration()
          .getDeploymentEnvironment()
          .getInitContainers()
          .put(getServiceName(), initContainers);
    }

    return KubernetesV2Service.super.getInitContainers(details);
  }

  // what an ugly method :(
  private Map getPluginDownloadingContainer(Plugins plugins) {
    List<Manifest> pluginManifests = new ArrayList<>();
    pluginManifests =
        plugins.getPlugins().stream()
            .filter(p -> p.getEnabled())
            .filter(p -> !p.getManifestLocation().isEmpty())
            .map(p -> p.generateManifest())
            .distinct()
            .collect(Collectors.toList());

    Map pluginDownloadingContainer = new HashMap();
    pluginDownloadingContainer.put("name", "plugin-downloader");
    pluginDownloadingContainer.put("image", "busybox");

    ArrayList command = new ArrayList();
    command.add("/bin/sh");
    pluginDownloadingContainer.put("command", command);

    pluginDownloadingContainer.put("args", getPluginDownloadingArgs(pluginManifests));

    List<Map> volumeMounts = new ArrayList();
    Map initContainerVolumeMount = new HashMap();
    initContainerVolumeMount.put("name", "downloaded-plugin-location");
    initContainerVolumeMount.put("mountPath", "/opt/spinnaker/plugins/resources");
    volumeMounts.add(initContainerVolumeMount);

    pluginDownloadingContainer.put("volumeMounts", volumeMounts);
    return pluginDownloadingContainer;
  }

  private List<String> getPluginDownloadingArgs(List<Manifest> manifests) {
    String manifestsList = String.join(" ",
      manifests
        .stream()
        .map(m -> m.getJars())
        .flatMap(Collection::stream)
        .collect(Collectors.toSet()));
    String arg = "for i in " + manifestsList + "; do wget $i; done";

    ArrayList pluginArgs = new ArrayList();
    pluginArgs.add("-c");
    pluginArgs.add(arg);
    return pluginArgs;
  }

  //@Override
  //public List<String> combineVolumes(
  //    List<ConfigSource> configSources,
  //    KubernetesSettings settings,
  //    List<SidecarConfig> sidecarConfigs,
  //    AccountDeploymentDetails<KubernetesAccount> details) {
  //  Plugins plugins = details.getDeploymentConfiguration().getPlugins();
  //  if (plugins.enabled && plugins.isDownloadingEnabled()) {
  //
  //    List<ConfigSource> volumes = settings.getVolumes();
  //    volumes.add(
  //        new ConfigSource()
  //          .setId("downloaded-plugin-location")
  //          .setMountPath("/opt/spinnaker/plugins/resources")
  //          .setType(ConfigSource.Type.emptyDir));
  //    settings.setVolumes(volumes);
  //    //settings
  //    //    .getVolumes()
  //    //    .add(
  //    //        new ConfigSource()
  //    //            .setId("downloaded-plugin-location")
  //    //            .setMountPath("/opt/spinnaker/plugins/resources")
  //    //            .setType(ConfigSource.Type.emptyDir));
  //  }
  //  return KubernetesV2Service.super.combineVolumes(
  //      configSources, settings, sidecarConfigs, details);
  //}


  @Override
  public List<ConfigSource> stageConfig(
    KubernetesV2Executor executor,
    AccountDeploymentDetails<KubernetesAccount> details,
    GenerateService.ResolvedConfiguration resolvedConfiguration) {

    List<ConfigSource> configSources = KubernetesV2Service.super.stageConfig(executor, details, resolvedConfiguration);

    Plugins plugins = details.getDeploymentConfiguration().getPlugins();
    if (plugins.enabled && plugins.isDownloadingEnabled()) {
          configSources.add(new ConfigSource()
            .setId("downloaded-plugin-location")
            .setMountPath("/opt/spinnaker/plugins/resources")
            .setType(ConfigSource.Type.emptyDir));
    }
    return configSources;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String path = Paths.get(settingsPath, settingsJs).toString();
    result.add(
        deckDockerProfileFactory.getProfile(settingsJs, path, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    KubernetesSharedServiceSettings kubernetesSharedServiceSettings =
        new KubernetesSharedServiceSettings(deploymentConfiguration);
    ServiceSettings settings = defaultServiceSettings(deploymentConfiguration);
    settings
        .setArtifactId(getArtifactId(deploymentConfiguration))
        .setLocation(kubernetesSharedServiceSettings.getDeployLocation())
        .setEnabled(true);
    return settings;
  }
}
