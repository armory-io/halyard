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
package com.netflix.spinnaker.halyard.controllers.v1;

import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.services.v1.DynamicValidationService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/validation")
public class ValidationController {
  private final DynamicValidationService validationService;

  @RequestMapping(value = "/config", method = RequestMethod.POST)
  List<Problem> validateConfig(
      MultipartHttpServletRequest request,
      @RequestParam(required = false) List<String> skipValidators,
      @RequestParam boolean failFast)
      throws IOException {
    Instant start = Instant.now();
    if (skipValidators == null) {
      skipValidators = new ArrayList<>();
    }
    List<Problem> res = validationService.validate(request, skipValidators, failFast);
    Instant end = Instant.now();
    log.info(String.format("Time elapsed: %s", Duration.between(start, end)));
    return res;
  }
}
