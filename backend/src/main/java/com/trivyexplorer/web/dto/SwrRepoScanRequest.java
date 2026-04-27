package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SwrRepoScanRequest {
  @NotNull private Long datasourceId;
  @NotBlank private String namespace;
  @NotBlank private String repoName;

  public Long getDatasourceId() {
    return datasourceId;
  }

  public void setDatasourceId(Long datasourceId) {
    this.datasourceId = datasourceId;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getRepoName() {
    return repoName;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }
}
