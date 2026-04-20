package com.trivyexplorer.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class BatchScanRequest {
  @NotNull private Long datasourceId;
  @NotEmpty private List<String> imageRefs;

  public Long getDatasourceId() {
    return datasourceId;
  }

  public void setDatasourceId(Long datasourceId) {
    this.datasourceId = datasourceId;
  }

  public List<String> getImageRefs() {
    return imageRefs;
  }

  public void setImageRefs(List<String> imageRefs) {
    this.imageRefs = imageRefs;
  }
}
