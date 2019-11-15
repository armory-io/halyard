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
package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
public class ValidationRun {
  private final List<Validator> validators;
  private final ApplicationContext applicationContext;
  private final DeploymentConfiguration deploymentConfiguration;
  private final boolean failFast;
  private List<NodeValidator> nodeValidators;
  private ValidationResults results = new ValidationResults();

  public ValidationResults run() {
    nodeValidators =
        validators.stream()
            .map(v -> new NodeValidator(v, getNodeFilter(v)))
            .collect(Collectors.toList());
    visitNode(deploymentConfiguration);
    return results;
  }

  private boolean visitNode(Node node) {
    if (!validateNode(node)) {
      return false;
    }

    NodeIterator children = node.getChildren();
    Node recurse;
    while ((recurse = children.getNext()) != null) {
      // Don't visit disabled features
      if (recurse instanceof CanEnabled && !((CanEnabled) recurse).isEnabled()) {
        continue;
      }
      if (!visitNode(recurse)) {
        return false;
      }
    }
    return true;
  }

  private boolean validateNode(Node node) {
    for (NodeValidator nodeValidator : nodeValidators) {
      ConfigProblemSetBuilder psBuilder = nodeValidator.validate(node);
      if (psBuilder != null) {
        results.add(psBuilder);
        if (failFast && psBuilder.hasErrorOrFatalProblems()) {
          return false;
        }
      }
    }
    return true;
  }

  private NodeFilter getNodeFilter(Validator validator) {
    Type type =
        ((ParameterizedType) validator.getClass().getGenericSuperclass())
            .getActualTypeArguments()[0];
    if (type instanceof TypeVariable) {
      type = ((TypeVariable) type).getBounds()[0];
    }
    if (type instanceof Class) {
      return new NodeFilter((Class) type);
    }
    throw new IllegalArgumentException(
        "Unable to determine validator type of " + validator.getClass().getName());
  }

  @RequiredArgsConstructor
  private class NodeValidator<T extends Node> {
    private final Validator<T> validator;
    private final NodeFilter filter;

    public ConfigProblemSetBuilder validate(T node) {
      if (filter.matches(node)) {
        ConfigProblemSetBuilder psBuilder = new ConfigProblemSetBuilder(applicationContext);
        psBuilder.setNode(node);
        validator.validate(psBuilder, node);
        return psBuilder;
      }
      return null;
    }
  }

  @Data
  public static class ValidationResults extends HashMap<String, List<Problem>> {

    public void add(ConfigProblemSetBuilder psBuilder) {
      ProblemSet pSet = psBuilder.build();
      for (Problem problem : pSet.getProblems()) {
        List<Problem> problems = get(problem.getLocation());
        if (problems == null) {
          problems = new ArrayList<>();
          put(problem.getLocation(), problems);
        }
        problems.add(problem);
      }
    }
  }
}
