package com.trivyexplorer.web.dto;

import java.time.Instant;

public record RegistryDatasourceResponse(
    Long id,
    String name,
    String type,
    String harborBaseUrl,
    String username,
    Instant createTime,
    Instant updateTime) {}
