package com.trivyexplorer.web.dto;

import java.time.Instant;

public record RegistryScanScheduleResponse(
    Long id,
    Long datasourceId,
    String datasourceName,
    String repoName,
    String imageRef,
    String cronExpression,
    boolean enabled,
    Instant lastRunAt,
    Instant createTime,
    Instant updateTime) {}
