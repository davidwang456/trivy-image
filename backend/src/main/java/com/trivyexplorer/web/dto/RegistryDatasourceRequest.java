package com.trivyexplorer.web.dto;

import jakarta.validation.constraints.NotBlank;

public class RegistryDatasourceRequest {
  @NotBlank private String name;
  private String type;
  private String harborBaseUrl;
  private String username;
  private String password;
  private String ak;
  private String sk;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getHarborBaseUrl() {
    return harborBaseUrl;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setHarborBaseUrl(String harborBaseUrl) {
    this.harborBaseUrl = harborBaseUrl;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getAk() {
    return ak;
  }

  public void setAk(String ak) {
    this.ak = ak;
  }

  public String getSk() {
    return sk;
  }

  public void setSk(String sk) {
    this.sk = sk;
  }
}
