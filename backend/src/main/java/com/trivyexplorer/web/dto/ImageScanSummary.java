package com.trivyexplorer.web.dto;

import java.time.Instant;

public record ImageScanSummary(
    Long id,
    String imageRef,
    String jobId,
    String jobName,
    String systemName,
    String projectName,
    String jobType,
    String createBy,
    String updateBy,
    Instant createTime,
    Instant updateTime) {}
