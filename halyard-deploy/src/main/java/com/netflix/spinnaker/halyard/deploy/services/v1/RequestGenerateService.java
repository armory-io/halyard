package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.google.common.io.Files;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.ServiceProviderFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import lombok.Getter;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public class RequestGenerateService extends GenerateService {
  @Getter File baseDirectory;

  public RequestGenerateService(
      DeploymentService deploymentService,
      ServiceProviderFactory serviceProviderFactory,
      List<SpinnakerService> spinnakerServices,
      ConfigParser configParser) {
    this.deploymentService = deploymentService;
    this.configParser = configParser;
    this.spinnakerServices = spinnakerServices;
    this.serviceProviderFactory = serviceProviderFactory;
    this.baseDirectory = Files.createTempDir();
    this.halconfigPath = baseDirectory.getAbsolutePath();
    this.halconfigDirectoryStructure =
        new HalconfigDirectoryStructure().setHalconfigDirectory(halconfigPath);
  }

  public void prepare(MultipartHttpServletRequest request) {
    HalconfigParser.setDirectoryOverride(baseDirectory);
    request.getFileMap().entrySet().stream()
        .forEach(
            et -> {
              File targetFile = new File(baseDirectory, et.getKey());
              targetFile.getParentFile().mkdirs();
              try {
                FileOutputStream outStream = new FileOutputStream(targetFile);
                outStream.write(et.getValue().getBytes());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  public void cleanup() {
    HalconfigParser.setDirectoryOverride(null);
  }
}
