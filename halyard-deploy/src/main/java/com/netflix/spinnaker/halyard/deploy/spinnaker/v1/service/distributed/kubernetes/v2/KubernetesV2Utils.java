/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.services.v1.FileService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.resource.v1.JinjaJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Slf4j
@Component
public class KubernetesV2Utils {
  private final ObjectMapper mapper = new ObjectMapper();

  private final SecretSessionManager secretSessionManager;

  private final CloudConfigResourceService cloudConfigResourceService;

  private final FileService fileService;

  public KubernetesV2Utils(
      SecretSessionManager secretSessionManager,
      CloudConfigResourceService cloudConfigResourceService,
      FileService fileService) {
    this.secretSessionManager = secretSessionManager;
    this.cloudConfigResourceService = cloudConfigResourceService;
    this.fileService = fileService;
  }

  public List<String> kubectlPrefix(KubernetesAccount account) {
    List<String> command = new ArrayList<>();
    command.add("kubectl");

    if (account.usesServiceAccount()) {
      return command;
    }

    String context = account.getContext();
    if (context != null && !context.isEmpty()) {
      command.add("--context");
      command.add(context);
    }

    Path kubeconfig = fileService.getLocalFilePath(account.getKubeconfigFile());
    if (kubeconfig != null) {
      command.add("--kubeconfig");
      command.add(kubeconfig.toString());
    }

    return command;
  }

  private String getKubeconfigFile(KubernetesAccount account) {
    String kubeconfigFile = account.getKubeconfigFile();

    if (EncryptedSecret.isEncryptedSecret(kubeconfigFile)) {
      return secretSessionManager.decryptAsFile(kubeconfigFile);
    }

    if (CloudConfigResourceService.isCloudConfigResource(kubeconfigFile)) {
      return cloudConfigResourceService.getLocalPath(kubeconfigFile);
    }

    return kubeconfigFile;
  }

  List<String> kubectlPodServiceCommand(
      KubernetesAccount account, String namespace, String service) {
    List<String> command = kubectlPrefix(account);

    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n=" + namespace);
    }

    command.add("get");
    command.add("po");

    command.add("-l=cluster=" + service);
    command.add("-o=jsonpath='{.items[0].metadata.name}'");

    return command;
  }

  List<String> kubectlConnectPodCommand(
      KubernetesAccount account, String namespace, String name, int port) {
    List<String> command = kubectlPrefix(account);

    if (StringUtils.isNotEmpty(namespace)) {
      command.add("-n=" + namespace);
    }

    command.add("port-forward");
    command.add(name);
    command.add(port + "");

    return command;
  }

  public String prettify(String input) {
    Yaml yaml = new Yaml(new SafeConstructor());
    return yaml.dump(yaml.load(input));
  }

  public Map<String, Object> parseManifest(String input) {
    Yaml yaml = new Yaml(new SafeConstructor());
    return mapper.convertValue(yaml.load(input), new TypeReference<Map<String, Object>>() {});
  }

  @Data
  public static class SecretSpec {
    TemplatedResource resource;
    String name;
  }

  @Data
  public static class SecretMountPair {
    File contents;
    byte[] contentBytes;
    String name;

    public SecretMountPair(File inputFile) {
      this(inputFile, inputFile);
    }

    public SecretMountPair(File inputFile, File outputFile) {
      this.contents = inputFile;
      this.name = outputFile.getName();
    }

    public SecretMountPair(String name, byte[] contentBytes) {
      this.contentBytes = contentBytes;
      this.name = name;
    }
  }
}
