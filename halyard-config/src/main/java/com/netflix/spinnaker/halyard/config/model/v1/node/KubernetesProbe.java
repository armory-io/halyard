package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KubernetesProbe {
  Integer initialDelaySeconds;
  Integer periodSeconds;
  Integer timeoutSeconds;
  Integer successThreshold;
  Integer failureThreshold;
  HttpGet httpGet;
  TcpSocket tcpSocket;
  Exec exec;

  @Data
  public static class TcpSocket {
    Integer port;
  }

  @Data
  public static class HttpGet {
    Integer port;
    String path;
    String scheme;
    List<HttpHeaders> httpHeaders = new ArrayList<>();
  }

  @Data
  public static class Exec {
    List<String> command = new ArrayList<>();
  }

  @Data
  public static class HttpHeaders {
    String name;
    String value;
  }
}
