package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportScanRequest {

  private String imageRef;

  @NotNull private JsonNode report;

  public String getImageRef() {
    return imageRef;
  }

  public void setImageRef(String imageRef) {
    this.imageRef = imageRef;
  }

  public JsonNode getReport() {
    return report;
  }

  public void setReport(JsonNode report) {
    this.report = report;
  }
}
