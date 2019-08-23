package com.netflix.spinnaker.halyard.cli.command.v1;

import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.bakery.AbstractEditBakeryDefaultsCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider.ProviderType;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import java.lang.reflect.Field;
import java.util.Arrays;
import lombok.Data;
import net.minidev.json.JSONObject;

@Data
public class GenerateMetadata {
  public static JSONObject generateMetadata() {
    EnrichMetadata em = new EnrichMetadata();
    for (ProviderType provider : ProviderType.values()) {
      try {
        Class<?> providerCommandClass = translateProviderCommandType(provider.getName());
        Field[] providerCommandFields = getFields(providerCommandClass);
        em.addProviderCommandFields(provider.getName(), providerCommandFields);

      } catch (IllegalArgumentException e) {
        // ignoring because it doesn't matter
      }
    }
    for (ProviderType provider : ProviderType.values()) {
      try {
        Class<?> addBakeryCommandClass = translateEditBakeryCommandType(provider.getName());
        Field[] addBakeryCommandFields = getFields(addBakeryCommandClass);
        em.addBakeryCommandFields(provider.getName(), addBakeryCommandFields);
      } catch (IllegalArgumentException e) {
        // ignoring because it doesn't matter
      }
    }
    for (ProviderType provider : ProviderType.values()) {
      try {
        Class<?> addCommandClass = translateAddAccountCommandType(provider.getName());
        Field[] addCommandFields = getFields(addCommandClass);
        em.addAccountCommandFields(provider.getName(), addCommandFields);
      } catch (IllegalArgumentException e) {
        // ignoring because it doesn't matter
      }
    }
    return em.getProvidersFields();
  }

  public static Class<? extends AbstractAddAccountCommand> translateAddAccountCommandType(
      String providerName) {
    Class<?> providerClass = Providers.translateProviderType(providerName);

    String addAccountCommandClass =
        providerClass
            .getName()
            .replaceAll(
                "com.netflix.spinnaker.halyard.config.model.v1.providers",
                "com.netflix.spinnaker.halyard.cli.command.v1.config.providers")
            .replaceAll("Provider", "AddAccountCommand");
    try {
      return (Class<? extends AbstractAddAccountCommand>) Class.forName(addAccountCommandClass);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No account for class \"" + addAccountCommandClass + "\" found", e);
    }
  }

  public static Class<? extends AbstractEditBakeryDefaultsCommand> translateEditBakeryCommandType(
      String providerName) {
    Class<?> providerClass = Providers.translateProviderType(providerName);

    String editBakeryDefaultsCommand =
        providerClass
            .getName()
            .replaceAll(
                "com.netflix.spinnaker.halyard.config.model.v1.providers",
                "com.netflix.spinnaker.halyard.cli.command.v1.config.providers")
            .replaceAll("Provider", "EditBakeryDefaultsCommand");
    try {
      return (Class<? extends AbstractEditBakeryDefaultsCommand>)
          Class.forName(editBakeryDefaultsCommand);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No account for class \"" + editBakeryDefaultsCommand + "\" found", e);
    }
  }

  public static Class<? extends AbstractProviderCommand> translateProviderCommandType(
      String providerName) {
    Class<?> providerClass = Providers.translateProviderType(providerName);

    String editBakeryDefaultsCommand =
        providerClass
            .getName()
            .replaceAll(
                "com.netflix.spinnaker.halyard.config.model.v1.providers",
                "com.netflix.spinnaker.halyard.cli.command.v1.config.providers")
            .replaceAll("Provider", "EditProviderCommand");
    try {
      return (Class<? extends AbstractEditBakeryDefaultsCommand>)
          Class.forName(editBakeryDefaultsCommand);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No account for class \"" + editBakeryDefaultsCommand + "\" found", e);
    }
  }

  public static Field[] getFields(Class<?> c) {
    Field[] extendedFields = c.getSuperclass().getDeclaredFields();
    Field[] fields = c.getDeclaredFields();
    Field[] allFields = new Field[extendedFields.length + fields.length];
    Arrays.setAll(
        allFields,
        i -> (i < extendedFields.length ? extendedFields[i] : fields[i - extendedFields.length]));
    return allFields;
  }
}
