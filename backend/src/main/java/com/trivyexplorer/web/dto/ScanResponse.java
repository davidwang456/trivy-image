package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ScanResponse(
    Long id,
    String imageRef,
    String jobId,
    String jobName,
    String systemName,
    String projectName,
    String jobType,
    String createBy,
    String updateBy,
    JsonNode report,
    Instant createTime,
    Instant updateTime) {}
