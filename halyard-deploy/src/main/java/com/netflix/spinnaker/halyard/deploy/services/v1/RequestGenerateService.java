package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.DeploymentService;
import com.netflix.spinnaker.halyard.deploy.config.v1.ConfigParser;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.ServiceProviderFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.yaml.snakeyaml.Yaml;

public class RequestGenerateService extends GenerateService {
  private final static String CONFIG_KEY = "config";
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

  public void prepare(MultipartHttpServletRequest request) throws IOException {
    HalconfigParser.setDirectoryOverride(baseDirectory);
    // Get the config first
    DeploymentConfiguration deploymentConfiguration = parseDeploymentConfiguration(request.getFile(CONFIG_KEY));
    writeHalConfig(deploymentConfiguration);


    // Loop through all files
    request.getFileMap().entrySet().stream()
        .forEach(
            et -> {
              if (!et.getKey().equals(CONFIG_KEY)) {
                File targetFile = getTargetFile(deploymentConfiguration, et.getKey());
                if (targetFile != null) {
                  targetFile.getParentFile().mkdirs();
                  try {
                    FileOutputStream outStream = new FileOutputStream(targetFile);
                    outStream.write(et.getValue().getBytes());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
              }
            });
  }

  public void cleanup() {
    HalconfigParser.setDirectoryOverride(null);
  }

  protected File getTargetFile(DeploymentConfiguration deploymentConfiguration, String paramKey) {
    String path = paramKey.replaceAll("__", File.separator);
    // Don't allow parent reference
    if (path.indexOf("..") > -1) {
      return null;
    }
    return new File(baseDirectory, deploymentConfiguration.getName() + File.separator + path);
  }


  protected void writeHalConfig(DeploymentConfiguration deploymentConfiguration) throws IOException {
    Yaml yaml = new Yaml();
    ObjectMapper objectMapper = new ObjectMapper();
    Halconfig config = new Halconfig();
    config.getDeploymentConfigurations().add(deploymentConfiguration);
    config.setCurrentDeployment(deploymentConfiguration.getName());

    File configFile = new File(baseDirectory, "config");
    FileOutputStream outputStream = new FileOutputStream(configFile);
    outputStream.write(yaml.dump(objectMapper.convertValue(config, Map.class)).getBytes());
  }

  protected DeploymentConfiguration parseDeploymentConfiguration(MultipartFile deploymentConfigFile) throws IOException {
    if (deploymentConfigFile == null) {
      throw new IllegalArgumentException("No deployment configuration file provided.");
    }
    Yaml yaml = new Yaml();
    ObjectMapper objectMapper = new ObjectMapper();
    Object obj = yaml.load(new ByteArrayInputStream(deploymentConfigFile.getBytes()));
    DeploymentConfiguration deploymentConfiguration = objectMapper.convertValue(obj, DeploymentConfiguration.class);
    if (deploymentConfiguration.getName() == null) {
      deploymentConfiguration.setName("default");
    }
    return deploymentConfiguration;
  }
}
