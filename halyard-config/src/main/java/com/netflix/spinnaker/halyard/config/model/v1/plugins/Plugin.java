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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

@Data
@EqualsAndHashCode(callSuper = true)
public class Plugin extends Node {
  public String name;
  public Boolean enabled;
  public String manifestLocation;

  @Setter(AccessLevel.NONE)
  public HashMap<String, Object> options = new HashMap<>();

  public void setOptions(HashMap<String, Object> options) {
    this.options = generateOptions(options);
  }

  @Override
  public String getNodeName() {
    return name;
  }

  public HashMap<String, Object> generateOptions(HashMap<String, Object> opts) {
    HashMap<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : opts.entrySet()) {
      String key = entry.getKey();
      result.putAll(parseOptions(key, entry.getValue()));
    }

    return result;
  }

  public HashMap<String, Object> merge(Map original, Map newMap) {
    for (Object key : newMap.keySet()) {
      if (newMap.get(key) instanceof Map && original.get(key) instanceof Map) {
        Map originalChild = (Map) original.get(key);
        Map newChild = (Map) newMap.get(key);
        original.put(key, merge(originalChild, newChild));
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
    return (HashMap<String, Object>) original;
  }

  private HashMap<String, Object> parseOptions(String key, Object value) {
    HashMap<String, Object> opts = new HashMap<>();
    if (!key.contains(".")) {
      opts.put(key, value);
      return opts;
    }

    String[] keys = key.split("\\.", 2);
    opts.put(keys[0], parseOptions(keys[1], value));
    return opts;
  }
}
