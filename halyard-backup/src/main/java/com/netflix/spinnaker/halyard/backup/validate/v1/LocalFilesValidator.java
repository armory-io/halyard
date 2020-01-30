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
 */
package com.netflix.spinnaker.halyard.backup.validate.v1;

import com.netflix.spinnaker.halyard.backup.services.v1.BackupService;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Ensures that all local file references in a DeploymentConfiguration can be resolved and copied to
 * staging area.
 */
@Component
public class LocalFilesValidator extends Validator<DeploymentConfiguration> {

  private final BackupService backupService;
  private final HalconfigDirectoryStructure directoryStructure;

  @Autowired
  public LocalFilesValidator(
      BackupService backupService, HalconfigDirectoryStructure directoryStructure) {
    this.backupService = backupService;
    this.directoryStructure = directoryStructure;
  }

  @Override
  public void validate(ConfigProblemSetBuilder p, DeploymentConfiguration n) {
    try {
      this.backupService.backupLocalFiles(
          n, directoryStructure.getStagingDependenciesPath(n.getName()).toString());
    } catch (HalException e) {
      Problem problem;
      String causeMessage =
          e.getCause() != null
              ? e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage()
              : "";

      if (!e.getProblems().getProblems().isEmpty()) {
        Problem downstream = e.getProblems().getProblems().get(0);
        problem =
            new ConfigProblemBuilder(
                    downstream.getSeverity(),
                    downstream.getMessage() + ". Caused by: " + causeMessage)
                .build();
      } else {
        problem =
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL, e.getMessage() + " Caused by: " + causeMessage)
                .build();
      }

      p.addProblem(problem.getSeverity(), problem.getMessage());
    }
  }
}
