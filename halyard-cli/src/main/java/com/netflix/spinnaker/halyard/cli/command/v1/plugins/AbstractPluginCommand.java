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

package com.netflix.spinnaker.halyard.cli.command.v1.plugins;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalConfigOptions;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import org.apache.commons.lang3.StringUtils;

/** An abstract definition for commands that accept plugins as a main parameter */
@Parameters(separators = "=")
public abstract class AbstractPluginCommand extends NestableCommand {
  @Parameter(
      names = {"--no-validate"},
      description = "Skip validation.")
  public boolean noValidate = false;

  @Parameter(
      names = {"--deployment"},
      description =
          "If supplied, use this Halyard deployment. This will _not_ create a new deployment.")
  public void setDeployment(String deployment) {
    GlobalConfigOptions.getGlobalConfigOptions().setDeployment(deployment);
  }

  protected String getCurrentDeployment() {
    String deployment = GlobalConfigOptions.getGlobalConfigOptions().getDeployment();
    if (StringUtils.isEmpty(deployment)) {
      deployment =
          new OperationHandler<String>()
              .setFailureMesssage("Failed to get deployment name.")
              .setOperation(Daemon.getCurrentDeployment())
              .get();
    }

    return deployment;
  }

  protected static boolean isSet(Object o) {
    return o != null;
  }
}
