package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SwrNamespaceScanRequest {
  @NotNull private Long datasourceId;
  @NotBlank private String namespace;

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
}
