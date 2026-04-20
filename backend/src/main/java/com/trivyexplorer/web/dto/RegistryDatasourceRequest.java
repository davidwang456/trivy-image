package com.trivyexplorer.web.dto;

import jakarta.validation.constraints.NotBlank;

public class RegistryDatasourceRequest {
  @NotBlank private String name;
  @NotBlank private String harborBaseUrl;
  @NotBlank private String username;
  @NotBlank private String password;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getHarborBaseUrl() {
    return harborBaseUrl;
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
}
