/*
 * Copyright 2019 Armory, Inc.
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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Plugins;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Manifest;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

@Slf4j
@Component
public class PluginProfileFactory extends StringBackedProfileFactory {
  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    Plugins plugins = deploymentConfiguration.getPlugins();

    Representer representer = new Representer();
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(new Constructor(Manifest.class), representer);

    Map<String, List<Map<String, Object>>> fullyRenderedYaml = new HashMap<>();
    List<Map<String, Object>> pluginMetadata = new ArrayList<>();

    final List<Plugin> plugin = plugins.getPlugin();
    for (Plugin p : plugin) {
      if (!p.getEnabled()) {
        log.info("Plugin " + p.getName() + ", not enabled");
        continue;
      }

      String manifestLocation = p.getManifestLocation();
      if (Objects.equals(manifestLocation, null)) {
        log.info("Plugin " + p.getName() + ", has no manifest file");
        continue;
      }

      InputStream manifestContents;
      try {
        if (manifestLocation.startsWith("http:") || manifestLocation.startsWith("https:")) {
          URL url = new URL(manifestLocation);
          manifestContents = url.openStream();
        } else {
          manifestContents = new FileInputStream(manifestLocation);
        }

        Manifest manifest = yaml.load(manifestContents);
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put("name", manifest.getName());
        metadata.put("enabled", p.getEnabled());
        metadata.put("jars", manifest.getJars());
        pluginMetadata.add(metadata);
      } catch (IOException e) {
        log.error("Cannot get plugin manifest file from: " + manifestLocation);
        log.error(e.getMessage());
      }
    }

    fullyRenderedYaml.put("plugins", pluginMetadata);

    profile.appendContents(
        yamlToString(deploymentConfiguration.getName(), profile, fullyRenderedYaml));
  }

  @Override
  protected String getRawBaseProfile() {
    return "";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.SPINNAKER;
  }

  @Override
  protected String commentPrefix() {
    return "## ";
  }
}
