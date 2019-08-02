package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Plugins extends Node {

  @Override
  public String getNodeName() {
    return "plugins";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
            plugin.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  private List<Plugin> plugin = new ArrayList<>();
  private boolean enabled;
}
