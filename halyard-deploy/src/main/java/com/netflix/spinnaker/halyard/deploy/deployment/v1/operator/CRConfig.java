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

package com.netflix.spinnaker.halyard.deploy.deployment.v1.operator;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class CRConfig {
  public static final Set<String> reservedNames =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("service-settings", "profiles")));
  private String name;
  private Map<String, Object> serviceSettings = new HashMap<>();
  private Map<String, String> profiles = new HashMap<>();
  private Map<String, String> requiredFiles = new HashMap<>();
  private DeploymentConfiguration deploymentConfiguration;
  private Map<String, String> absToRelativeMap = new HashMap<>();

  public String getName() {
    if (StringUtils.isEmpty(name)) {
      return deploymentConfiguration.getName();
    }
    return name;
  }

  protected String assignRelativeFileName(Path path) {
    if (absToRelativeMap.containsKey(path.toString())) {
      return absToRelativeMap.get(path.toString());
    }

    // Form the reference from the filename
    String base = path.getFileName().toString();

    for (int i = 0; ; i++) {
      String relative = i == 0 ? base : base + "-" + i;
      if (!requiredFiles.containsKey(relative) || reservedNames.contains(relative)) {
        absToRelativeMap.put(path.toString(), relative);
        return relative;
      }
    }
  }

  public void stageRequiredFiles(String filesRelativeTo) {
    Consumer<Node> fileFinder =
        n ->
            n.localFiles()
                .forEach(
                    f -> {
                      try {
                        f.setAccessible(true);
                        String fPath = (String) f.get(n);
                        if (StringUtils.isEmpty(fPath)) {
                          return;
                        }
                        Path absPath = Paths.get(fPath);
                        if (!fPath.startsWith(File.separator)) {
                          absPath = Paths.get(filesRelativeTo, fPath);
                        }

                        String name = assignRelativeFileName(absPath);

                        try {
                          requiredFiles.put(name, new String(Files.readAllBytes(absPath)));
                        } catch (IOException e) {
                          throw new HalException(Problem.Severity.ERROR, e.getMessage(), e);
                        }

                        f.set(n, name);

                      } catch (IllegalAccessException e) {
                        throw new RuntimeException(
                            "Failed to get local files for node " + n.getNodeName(), e);
                      } finally {
                        f.setAccessible(false);
                      }
                    });

    deploymentConfiguration.recursiveConsume(fileFinder);
  }
}
