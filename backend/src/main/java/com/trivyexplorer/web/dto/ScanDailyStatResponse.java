package com.trivyexplorer.web.dto;

/** One calendar day of aggregates from {@code image_scans} (by {@code create_time}). */
public record ScanDailyStatResponse(String date, long jobTotal, long imageRefTotal) {}
