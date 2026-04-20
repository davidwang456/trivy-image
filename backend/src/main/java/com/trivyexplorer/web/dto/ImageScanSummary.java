package com.trivyexplorer.web.dto;

import java.time.Instant;

public record ImageScanSummary(Long id, String imageRef, Instant createTime, Instant updateTime) {}
