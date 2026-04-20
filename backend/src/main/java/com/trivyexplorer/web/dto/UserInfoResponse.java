package com.trivyexplorer.web.dto;

import java.time.Instant;

public record UserInfoResponse(
    Long id,
    String username,
    String role,
    Instant createTime,
    Instant updateTime,
    Boolean mustChangePassword) {}
