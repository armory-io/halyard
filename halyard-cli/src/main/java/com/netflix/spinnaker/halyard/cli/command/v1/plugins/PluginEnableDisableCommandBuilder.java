package com.netflix.spinnaker.halyard.cli.command.v1.plugins;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.CommandBuilder;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalConfigOptions;
import com.netflix.spinnaker.halyard.cli.command.v1.NestableCommand;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

public class PluginEnableDisableCommandBuilder implements CommandBuilder {
  @Setter boolean enable;

  @Override
  public NestableCommand build() {
    return new PluginEnableDisableCommand(enable);
  }

  @Parameters(separators = "=")
  private static class PluginEnableDisableCommand extends NestableCommand {
    private PluginEnableDisableCommand(boolean enable) {
      this.enable = enable;
    }

    @Getter(AccessLevel.PROTECTED)
    boolean enable;

    @Parameter(
        names = {"--no-validate"},
        description = "Skip validation.")
    public boolean noValidate = false;

    @Override
    public String getShortDescription() {
      return "Enable or disable all plugins";
    }

    @Override
    public String getCommandName() {
      return isEnable() ? "enable" : "disable";
    }

    private String indicativePastPerfectAction() {
      return isEnable() ? "enabled" : "disabled";
    }

    @Override
    protected void executeThis() {
      String currentDeployment = getCurrentDeployment();
      boolean enable = isEnable();
      new OperationHandler<Void>()
          .setSuccessMessage("Successfully " + indicativePastPerfectAction() + " all plugins")
          .setFailureMesssage("Failed to " + getCommandName() + " all plugins")
          .setOperation(Daemon.setPluginEnableDisable(currentDeployment, !noValidate, enable))
          .get();
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
  }
}
