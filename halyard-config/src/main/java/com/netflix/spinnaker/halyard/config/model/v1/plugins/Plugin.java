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

package com.netflix.spinnaker.halyard.config.model.v1.plugins;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

@Data
@EqualsAndHashCode(callSuper = true)
public class Plugin extends Node {
  public String name;
  public Boolean enabled;
  public String manifestLocation;
  public Map<String, Object> options = new HashMap<>();

  @Override
  public String getNodeName() {
    return name;
  }

  public Plugin updateOptions(Map<String, String> options) {
    Map<String, Object> parsedOptions = new HashMap<>();
    for (Map.Entry<String, String> option : options.entrySet()) {
      parsedOptions.put(option.getKey(), option.getValue());
    }
    setOptions(Plugin.merge(getOptions(), Plugin.normalizeOptions(parsedOptions)));
    return this;
  }

  public Manifest generateManifest() {
    Representer representer = new Representer();
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(new Constructor(Manifest.class), representer);

    InputStream manifestContents = downloadManifest();
    Manifest manifest = yaml.load(manifestContents);
    manifest.validate();
    return manifest;
  }

  public InputStream downloadManifest() {
    try {
      if (manifestLocation.startsWith("http:") || manifestLocation.startsWith("https:")) {
        URL url = new URL(manifestLocation);
        return url.openStream();
      } else {
        return new FileInputStream(manifestLocation);
      }
    } catch (IOException e) {
      throw new HalException(
          new ProblemBuilder(
                  Problem.Severity.FATAL,
                  "Cannot get plugin manifest file from: "
                      + manifestLocation
                      + ": "
                      + e.getMessage())
              .build());
    }
  }

  public static Map<String, Object> normalizeOptions(Map<String, Object> opts) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : opts.entrySet()) {
      String key = entry.getKey();
      result.putAll(parseOption(key, entry.getValue()));
    }

    return result;
  }

  public static Map<String, Object> merge(Map original, Map newMap) {
    for (Object key : newMap.keySet()) {
      if (newMap.get(key) instanceof Map && original.get(key) instanceof Map) {
        Map originalChild = (Map) original.get(key);
        Map newChild = (Map) newMap.get(key);
        original.put(key, Plugin.merge(originalChild, newChild));
      } else if (newMap.get(key) instanceof List && original.get(key) instanceof List) {
        List originalChild = (List) original.get(key);
        List newChild = (List) newMap.get(key);
        for (Object each : newChild) {
          if (!originalChild.contains(each)) {
            originalChild.add(each);
          }
        }
      } else {
        original.put(key, newMap.get(key));
      }
    }
    return (Map<String, Object>) original;
  }

  public static Map<String, Object> parseOption(String key, Object value) {
    Map<String, Object> opts = new HashMap<>();
    if (!key.contains(".")) {
      opts.put(key, value);
      return opts;
    }

    String[] keys = key.split("\\.", 2);
    opts.put(keys[0], parseOption(keys[1], value));
    return opts;
  }
}
