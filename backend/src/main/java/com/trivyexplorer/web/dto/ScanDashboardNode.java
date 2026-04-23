package com.trivyexplorer.web.dto;

import java.time.Instant;
import java.util.List;

public record ScanDashboardNode(
    String jobId,
    String jobName,
    List<SystemNode> systems,
    Instant createTime,
    Instant updateTime) {

  public record SystemNode(String systemName, List<ProjectNode> projects) {}

  public record ProjectNode(String projectName, List<ImageNode> images) {}

  public record ImageNode(
      Long id, String imageRef, Instant createTime, Instant updateTime, String jobId, String jobName) {}
}
