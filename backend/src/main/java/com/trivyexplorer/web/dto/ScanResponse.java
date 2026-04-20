package com.trivyexplorer.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ScanResponse(
    Long id,
    String imageRef,
    JsonNode report,
    Instant createTime,
    Instant updateTime) {}
