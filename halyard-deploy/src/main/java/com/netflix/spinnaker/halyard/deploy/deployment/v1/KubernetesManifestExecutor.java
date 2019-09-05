package com.netflix.spinnaker.halyard.deploy.deployment.v1;

import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.resource.v1.JinjaJarResource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.kubernetes.v2.KubernetesV2Utils;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;

public abstract class KubernetesManifestExecutor {

  public KubernetesV2Utils.SecretSpec createSecretSpec(
      String namespace,
      String clusterName,
      String name,
      List<KubernetesV2Utils.SecretMountPair> files) {
    Map<String, String> contentMap = new HashMap<>();
    for (KubernetesV2Utils.SecretMountPair pair : files) {
      String contents;
      if (pair.getContentBytes() != null) {
        contents = new String(Base64.getEncoder().encode(pair.getContentBytes()));
      } else {
        try {
          contents =
              new String(
                  Base64.getEncoder()
                      .encode(IOUtils.toByteArray(new FileInputStream(pair.getContents()))));
        } catch (IOException e) {
          throw new HalException(
              Problem.Severity.FATAL,
              "Failed to read required config file: "
                  + pair.getContents().getAbsolutePath()
                  + ": "
                  + e.getMessage(),
              e);
        }
      }

      contentMap.put(pair.getName(), contents);
    }

    KubernetesV2Utils.SecretSpec spec = new KubernetesV2Utils.SecretSpec();
    spec.setName(name + "-" + Math.abs(contentMap.hashCode()));

    spec.setResource(new JinjaJarResource("/kubernetes/manifests/secret.yml"));
    Map<String, Object> bindings = new HashMap<>();

    bindings.put("files", contentMap);
    bindings.put("name", spec.getName());
    bindings.put("namespace", namespace);
    bindings.put("clusterName", clusterName);

    spec.getResource().extendBindings(bindings);

    return spec;
  }

  public boolean exists(String manifest) {
    return false;
  }

  public abstract void replace(String manifest);
}
