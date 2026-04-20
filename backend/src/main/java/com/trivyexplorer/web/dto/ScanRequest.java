package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanRequest {
  @NotNull private Long datasourceId;
  @NotBlank private String imageRef;

  public Long getDatasourceId() {
    return datasourceId;
  }

  public void setDatasourceId(Long datasourceId) {
    this.datasourceId = datasourceId;
  }

  public String getImageRef() {
    return imageRef;
  }

  public void setImageRef(String imageRef) {
    this.imageRef = imageRef;
  }
}
