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
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import lombok.AccessLevel;
import lombok.Getter;

@Parameters(separators = "=")
public class EditPluginCommand extends AbstractHasPluginCommand {
  @Getter(AccessLevel.PUBLIC)
  private String commandName = "edit";

  @Getter(AccessLevel.PUBLIC)
  private String shortDescription = "Edit a plugin";

  @Parameter(
      names = "--manifest-location",
      description = "The location of the plugin's manifest file.")
  private String manifestLocation;

  @Parameter(
    names = "--enabled",
    description = "To enable or disable the plugin")
  private Boolean enabled = false;

  @Override
  protected void executeThis() {
    String currentDeployment = getCurrentDeployment();
    String name = getPlugin();
    Plugin plugin = new Plugin().setName(name).setManifestLocation(manifestLocation).setEnabled(enabled);

    new OperationHandler<Void>()
        .setFailureMesssage("Failed to edit plugin " + name + ".")
        .setSuccessMessage("Successfully edited plugin " + name + ".")
        .setOperation(Daemon.setPlugin(currentDeployment, name, !noValidate, plugin))
        .get();
  }
}
