package com.netflix.spinnaker.halyard.deploy.services.v1;

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.canary.CanaryAccountValidator;
import com.netflix.spinnaker.halyard.config.validate.v1.canary.CanaryValidator;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ValidationService {
  @Autowired HalconfigParser halconfigParser;
  @Autowired
  ApplicationContext applicationContext;
  @Autowired
  List<Validator> allValidators = new ArrayList<>();
  @Autowired
  List<Validator<? extends Provider>> providerValidators = new ArrayList<>();
  @Autowired
  List<Validator<? extends Account>> accountValidators = new ArrayList<>();
  @Autowired
  List<Validator<? extends PersistentStore>> persistentStoreValidators = new ArrayList<>();
  @Autowired
  List<Validator<? extends BakeryDefaults>> bakeryValidators = new ArrayList<>();
  @Autowired
  CanaryValidator canaryValidator;
  @Autowired
  List<CanaryAccountValidator> canaryAccountValidators = new ArrayList<>();
  @Autowired
  List<Validator<DeploymentEnvironment>> deploymentEnvironmentValidators = new ArrayList<>();


  public ProblemSet validate(MultipartHttpServletRequest request, List<String> skipValidators, boolean failFast) throws IOException {
    RequestGenerateService fileRequestService = newRequestGenerateService();
    try {
      log.info("Preparing Halyard configuration from incoming request");
      fileRequestService.prepare(request);

      log.info("Parsing Halyard config");
      Halconfig halConfig = halconfigParser.getHalconfig();
      DeploymentConfiguration deploymentConfiguration = halConfig.getDeploymentConfigurations().get(0);


      List<Validator> validators = allValidators
              .stream()
              .filter(v -> !skipValidators.contains(v.getClass().getSimpleName()))
              .collect(Collectors.toList());

      ConfigProblemSetBuilder problems = new ConfigProblemSetBuilder(applicationContext);
//      runProviderValidators(problems, deploymentConfiguration, skipValidators, failFast);

      List<NodeValidator> nodeValidators = validators
              .stream()
              .filter(v -> !skipValidators.contains(v.getClass().getSimpleName()))
              .map(v -> new NodeValidator(v, getNodeFilter(v)))
              .collect(Collectors.toList());

      log.info("Running {} validators", validators.size());

      visitNode(problems, nodeValidators, failFast, halConfig);
      return problems.build();

    } finally {
      fileRequestService.cleanup();
    }
  }

//  private boolean runProviderValidators(ConfigProblemSetBuilder psBuilder,
//                                        DeploymentConfiguration deploymentConfiguration,
//                                        List<String> skipValidators,
//                                        boolean failFast) {
//    List<NodeValidator> nodeValidators = providerValidators
//            .stream()
//            .filter(v -> !skipValidators.contains(v.getClass().getSimpleName()))
//            .map(v -> new NodeValidator(v, getNodeFilter(v)))
//            .collect(Collectors.toList());
//
//    Providers providers = deploymentConfiguration.getProviders();
//    NodeIterator iterator = providers.getChildren();
//    Node node;
//    while ((node = iterator.getNext()) != null) {
//      if (node instanceof Provider) {
//        Provider p = (Provider) node;
//        if (p.isEnabled()) {
//          for (NodeValidator v : nodeValidators) {
//            if (v.validate(psBuilder, p)) {
//              if (failFast && psBuilder.hasErrorOrFatalProblems()) {
//                return false;
//              }
//            }
//          }
//          if (!runProviderAccountValidators(psBuilder, p, skipValidators, failFast)) {
//            return false;
//          }
//        }
//      }
//    }
//    return true;
//  }
//
//  private boolean runProviderAccountValidators(ConfigProblemSetBuilder psBuilder,
//                                               Provider provider,
//                                               List<String> skipValidators,
//                                               boolean failFast) {
//    List<NodeValidator> nodeValidators = accountValidators
//            .stream()
//            .filter(v -> !skipValidators.contains(v.getClass().getSimpleName()))
//            .map(v -> new NodeValidator(v, getNodeFilter(v)))
//            .collect(Collectors.toList());
//
//    for (Account account : (List<? extends Account>)provider.getAccounts()) {
//      for (NodeValidator v : nodeValidators) {
//        if (v.validate(psBuilder, account)) {
//          if (failFast && psBuilder.hasErrorOrFatalProblems()) {
//            return false;
//          }
//        }
//      }
//    }
//    return true;
//  }

  private boolean visitNode(ConfigProblemSetBuilder psBuilder, List<NodeValidator> nodeValidators, boolean failFast, Node node) {
    if (!validateNode(psBuilder, node, nodeValidators, failFast)) {
      return false;
    }

    NodeIterator children = node.getChildren();
    Node recurse;
    while ((recurse = children.getNext()) != null) {
      // Don't visit disabled features
      if (node instanceof CanEnabled && !((CanEnabled) node).isEnabled()) {
        return true;
      }
      if (!visitNode(psBuilder, nodeValidators, failFast, recurse)) {
        return false;
      }
    }
    return true;
  }


  private boolean validateNode(ConfigProblemSetBuilder psBuilder, Node node, List<NodeValidator> nodeValidators, boolean failFast) {
    for (NodeValidator nodeValidator: nodeValidators) {
      if (nodeValidator.validate(psBuilder, node)) {
        if (failFast && psBuilder.hasErrorOrFatalProblems()) {
          return false;
        }
      }
    }
    return true;
  }

  protected RequestGenerateService newRequestGenerateService() {
    return new RequestGenerateService();
  }

//  private boolean isNodeDisabled(Node node) {
//    if (node instanceof Provider) {
//      return !((Provider) node).isEnabled();
//    }
//    try {
//      Method m = node.getClass().getDeclaredMethod("isEnabled");
//      Object r = m.invoke(node);
//      if (r instanceof Boolean) {
//        return !((Boolean) r).booleanValue();
//      }
//    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
//    }
//    return false;
//  }


//  private void recursiveValidate(ConfigProblemSetBuilder psBuilder, Node node, NodeFilter filter, NodeFilter visitingFilter, Validator validator) {
//    if (filter.matches(node)) {
//      int runCount = runMatchingValidators(psBuilder, validator, node, node.getClass()) ? 1 : 0;
//
//      log.info(
//              "Ran "
//                      + runCount
//                      + " validator " + validator.getClass().getSimpleName() + " for node \""
//                      + node.getNodeName()
//                      + "\" with class \""
//                      + node.getClass().getSimpleName()
//                      + "\"");
//    }
//
//    NodeIterator children = node.getChildren();
//
//    Node recurse = children.getNext(visitingFilter);
//    while (recurse != null) {
//      recursiveValidate(psBuilder, recurse, filter, visitingFilter, validator);
//      recurse = children.getNext(filter);
//    }
//  }


//  /**
//   * Walk up the object hierarchy, running this validator whenever possible. The idea is, perhaps we
//   * were passed a Kubernetes account, and want to run both the standard Kubernetes account
//   * validator to see if the kubeconfig is valid, as well as the super-classes Account validator to
//   * see if the account name is valid.
//   *
//   * @param psBuilder contains the list of problems encountered.
//   * @param validator is the validator to be run.
//   * @param node is the subject of validation.
//   * @param c is some super(inclusive) class of node.
//   * @return true iff the validator ran on the node (for logging purposes).
//   */
//  private boolean runMatchingValidators(
//          ConfigProblemSetBuilder psBuilder, Validator validator, Node node, Class c) {
//    if (c == Object.class) {
//      return false;
//    }
//
//    try {
//      Method m = validator.getClass().getMethod("validate", ConfigProblemSetBuilder.class, c);
//      m.invoke(validator, psBuilder, node);
//      return true;
//    } catch (InvocationTargetException | NoSuchMethodException e) {
//      // Do nothing, odds are most validators don't validate every class.
//    } catch (IllegalAccessException e) {
//      throw new RuntimeException(
//              "Failed to invoke validate() on \""
//                      + validator.getClass().getSimpleName()
//                      + "\" for node \""
//                      + c.getSimpleName(),
//              e);
//    }
//
//    return runMatchingValidators(psBuilder, validator, node, c.getSuperclass());
//  }

  private NodeFilter getNodeFilter(Validator validator) {
    Type type = ((ParameterizedType) validator.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    if (type instanceof TypeVariable) {
      type = ((TypeVariable) type).getBounds()[0];
    }
    if (type instanceof Class) {
      return new NodeFilter((Class) type);
    }
    throw new IllegalArgumentException("Unable to determine validator type of " + validator.getClass().getName());
  }

  @RequiredArgsConstructor
  private class NodeValidator<T extends Node> {
    private final Validator<T> validator;
    private final NodeFilter filter;

    public boolean validate(ConfigProblemSetBuilder psBuilder, T node) {
      if (filter.matches(node)) {
        log.info("Calling " + validator.getClass().getSimpleName() + " on node " + node.getNodeName());
        validator.validate(psBuilder, node);
        return true;
      }
      return false;
    }
  }
}
