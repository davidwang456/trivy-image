package com.trivyexplorer.web.dto;

/** Aggregates from {@code image_scans} for dashboard charts. */
public record ScanStatsResponse(long jobTotal, long imageRefTotal) {}
