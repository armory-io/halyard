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

package com.netflix.spinnaker.halyard.deploy.util.v1;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PluginUtils {
  public static InputStream getManifest(String manifestLocation) {
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
}
