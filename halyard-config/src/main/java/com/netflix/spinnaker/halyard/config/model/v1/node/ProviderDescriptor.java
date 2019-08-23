
package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.Data;
import net.minidev.json.JSONObject;

@Data
public class ProviderDescriptor {
  public JSONObject allProviderFields = new JSONObject();
  public JSONObject providersFields = new JSONObject();

  public JSONObject initialProviderFields() {
    // Fields for most providers: enabled, account, primaryAccount
    JSONObject providerFields = new JSONObject();
    Field[] initialFields = Provider.class.getDeclaredFields();
    for (Field field : initialFields) {
      providerFields.put(field.getName(), fieldType(field));
    }
    return providerFields;
  }

  public JSONObject initialAccountFields() {
    // Fields for most accounts: name, version ect
    JSONObject accountFields = new JSONObject();
    Field[] initialFields = Account.class.getDeclaredFields();
    for (Field field : initialFields) {
      accountFields.put(field.getName(), fieldType(field));
    }
    return accountFields;
  }

  public JSONObject initialBakeryFields() {
    JSONObject bakeryFields = new JSONObject();
    Field[] initialFields = BakeryDefaults.class.getDeclaredFields();
    for (Field field : initialFields) {
      bakeryFields.put(field.getName(), fieldType(field));
    }
    return bakeryFields;
  }

  public void addProviderField(String providerName, Field[] extraFields) {
    // some providers e.g aws, appengine have extra fields
    JSONObject addFields = initialProviderFields();
    for (Field field : extraFields) {
      addFields.put(field.getName(), fieldType(field));
    }
    if (providerName != null) {
      providersFields.put(providerName, addFields);
    }
  }

  public void addAccountField(String providerName, Field[] fields) {
    JSONObject accountFields = initialAccountFields();
    for (Field field : fields) {
      accountFields.put(field.getName(), fieldType(field));
    }
    if (providersFields != null && providersFields.get(providerName) != null) {
      ((HashMap<String, Object>) providersFields.get(providerName)).put("accountFields", accountFields);

    }
  }

  public void addBakeryField(String providerName, Field[] fields) {
    JSONObject bakeryFields = initialBakeryFields();
    for (Field field : fields) {
      bakeryFields.put(field.getName(), fieldType(field));
    }
    if (providersFields != null && providersFields.get(providerName) != null) {
      ((HashMap<String, Object>) providersFields.get(providerName)).put("useBakeryDefaults", "boolean");
      ((HashMap<String, Object>) providersFields.get(providerName)).put("bakeryDefaultsFields", bakeryFields);
    }
  }

  public JSONObject allProviderFields() {
    allProviderFields.put("providers", providersFields);
    return allProviderFields;
  }

  public JSONObject fieldType(Field field) {
    JSONObject typeWrapper = new JSONObject();
    if (field.getAnnotation(LocalFile.class) != null) {
      typeWrapper.put("type", "upload");
    } else if (field.getAnnotation(Secret.class) != null) {
      typeWrapper.put("type", "password");
    } else if (field.getType().isEnum()) {
      Object[] objects = field.getType().getEnumConstants();
      List<Object> enumConstantsList = new ArrayList<>();
      for (Object obj : objects) {
        enumConstantsList.add(obj);
      }
      typeWrapper.put("enum", enumConstantsList);
      typeWrapper.put("type", "string");

    } else if (field.getType() == (List.class) && !field.getName().equals("accounts")) {
      // assume a list is a list of strings
      typeWrapper.put("type", "stringlist");
    } else {
      typeWrapper.put("type", field.getType().getSimpleName().toLowerCase());
    }
    return typeWrapper;
  }

}