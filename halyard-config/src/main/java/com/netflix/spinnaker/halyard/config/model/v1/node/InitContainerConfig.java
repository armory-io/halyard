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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

// Not sure why this isn't a thing already, DeploymentEnvironment has init containers defined as
// maps,
// possibly for flexibility?
@Data
public class InitContainerConfig {
  String name = "custom-init-container";
  String dockerImage;
  // Integer port;
  List<Map<String, String>> env = new ArrayList<>();
  List<String> args = new ArrayList<>();
  List<String> command = new ArrayList<>();
  List<VolumeMount> empty = new ArrayList<>();

  @Data
  public static class VolumeMount {
    String name;
    String mountPath;
  }
}
