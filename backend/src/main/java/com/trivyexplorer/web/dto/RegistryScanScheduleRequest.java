package com.trivyexplorer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegistryScanScheduleRequest {
  @NotNull private Long datasourceId;
  private String repoName;
  private String imageRef;
  @NotBlank private String cronExpression;
  private boolean enabled = true;

  public Long getDatasourceId() {
    return datasourceId;
  }

  public void setDatasourceId(Long datasourceId) {
    this.datasourceId = datasourceId;
  }

  public String getRepoName() {
    return repoName;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }

  public String getImageRef() {
    return imageRef;
  }

  public void setImageRef(String imageRef) {
    this.imageRef = imageRef;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
