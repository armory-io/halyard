package com.netflix.spinnaker.halyard.cli.command.v1;

import java.lang.reflect.Field;
import java.util.HashMap;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;

import lombok.Data;
import net.minidev.json.JSONObject;
@Data
public class EnrichMetadata {
  public JSONObject providersFields = Providers.providersMetadata();

  public void addProviderCommandFields(String providerName, Field[] fields) {
    JSONObject providerFields = (JSONObject) ((JSONObject) providersFields.get("providers")).get(providerName);
    if (providersFields != null && providerFields != null) {
      for (Field f : fields) {
        if (f.getAnnotation(Parameter.class) != null && providerFields.get(f.getName()) != null) {
          if (f.getAnnotation(Parameter.class).required() == true) {
            ((HashMap<String, Object>) providerFields.get(f.getName())).put("required", true);
          }
          if (f.getAnnotation(Parameter.class).description() != "") {
            ((HashMap<String, Object>) providerFields.get(f.getName()))
                .put("description", f.getAnnotation(Parameter.class).description());
          }
        }
      }
    }
  }
  public void addAccountCommandFields(String providerName, Field[] fields) {
    JSONObject accountfields = (JSONObject) ((JSONObject) ((JSONObject) providersFields.get("providers")).get(providerName))
        .get("accountFields");
    if (providersFields != null && accountfields != null) {
      for (Field f : fields) {
        if (f.getAnnotation(Parameter.class) != null && accountfields.get(f.getName()) != null) {
          if (f.getAnnotation(Parameter.class).required() == true) {
            ((HashMap<String, Object>) accountfields.get(f.getName())).put("required", true);
          }
          if (f.getAnnotation(Parameter.class).description() != "") {
            ((HashMap<String, Object>) accountfields.get(f.getName()))
                .put("description", f.getAnnotation(Parameter.class).description());
          }
        }
      }
    }
  }
  public void addBakeryCommandFields(String providerName, Field[] fields) {
    JSONObject bakeryDefaultsFields = (JSONObject) ((JSONObject) ((JSONObject) providersFields.get("providers"))
        .get(providerName)).get("bakeryDefaultsFields");
    if (providersFields != null && bakeryDefaultsFields != null) {
      for (Field f : fields) {
        if (f.getAnnotation(Parameter.class) != null && bakeryDefaultsFields.get(f.getName()) != null) {
          if (f.getAnnotation(Parameter.class).required() == true) {
            ((HashMap<String, Object>) bakeryDefaultsFields.get(f.getName())).put("required", true);
          }
          if (f.getAnnotation(Parameter.class).description() != "") {
            ((HashMap<String, Object>) bakeryDefaultsFields.get(f.getName()))
                .put("description", f.getAnnotation(Parameter.class).description());
          }
        }
      }
    }
  }
  
}
