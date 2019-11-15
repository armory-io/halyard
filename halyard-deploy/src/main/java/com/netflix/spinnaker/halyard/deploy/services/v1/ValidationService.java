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
package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Slf4j
@Component
public class ValidationService {
  @Autowired HalconfigParser halconfigParser;
  @Autowired ApplicationContext applicationContext;
  @Autowired List<Validator> allValidators = new ArrayList<>();

  public ValidationRun.ValidationResults validate(
      MultipartHttpServletRequest request, List<String> skipValidators, boolean failFast)
      throws IOException {
    RequestGenerateService fileRequestService = newRequestGenerateService();
    try {
      log.info("Preparing Halyard configuration from incoming request");
      fileRequestService.prepare(request);

      log.info("Parsing Halyard config");
      Halconfig halConfig = halconfigParser.getHalconfig();

      List<Validator> validators =
          allValidators.stream()
              .filter(v -> !skipValidators.contains(v.getClass().getSimpleName()))
              .collect(Collectors.toList());

      log.info("Running {} validators", validators.size());
      ValidationRun run =
          new ValidationRun(
              validators,
              applicationContext,
              halConfig.getDeploymentConfigurations().get(0),
              failFast);
      return run.run();
    } finally {
      fileRequestService.cleanup();
    }
  }

  protected RequestGenerateService newRequestGenerateService() {
    return new RequestGenerateService();
  }
}
