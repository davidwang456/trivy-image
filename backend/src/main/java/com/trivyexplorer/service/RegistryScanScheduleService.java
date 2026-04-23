package com.trivyexplorer.service;

import com.trivyexplorer.domain.RegistryDatasource;
import com.trivyexplorer.domain.RegistryScanSchedule;
import com.trivyexplorer.repo.RegistryScanScheduleRepository;
import com.trivyexplorer.web.dto.RegistryScanScheduleRequest;
import com.trivyexplorer.web.dto.RegistryScanScheduleResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistryScanScheduleService {
  private final RegistryScanScheduleRepository scheduleRepository;
  private final RegistryDatasourceService registryDatasourceService;
  private final TrivyScanService trivyScanService;

  public RegistryScanScheduleService(
      RegistryScanScheduleRepository scheduleRepository,
      RegistryDatasourceService registryDatasourceService,
      TrivyScanService trivyScanService) {
    this.scheduleRepository = scheduleRepository;
    this.registryDatasourceService = registryDatasourceService;
    this.trivyScanService = trivyScanService;
  }

  @Transactional(readOnly = true)
  public List<RegistryScanScheduleResponse> list() {
    return scheduleRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional
  public RegistryScanScheduleResponse create(RegistryScanScheduleRequest request) {
    validateRequest(request);
    RegistryScanSchedule s = new RegistryScanSchedule();
    applyRequest(s, request);
    return toResponse(scheduleRepository.save(s));
  }

  @Transactional
  public RegistryScanScheduleResponse update(Long id, RegistryScanScheduleRequest request) {
    validateRequest(request);
    RegistryScanSchedule s = getById(id);
    applyRequest(s, request);
    return toResponse(scheduleRepository.save(s));
  }

  @Transactional
  public void delete(Long id) {
    scheduleRepository.deleteById(id);
  }

  @Transactional
  public void runDueSchedules() {
    Instant now = Instant.now();
    for (RegistryScanSchedule s : scheduleRepository.findByEnabledTrue()) {
      CronExpression cron;
      try {
        cron = CronExpression.parse(s.getCronExpression());
      } catch (IllegalArgumentException e) {
        continue;
      }
      Instant base = s.getLastRunAt() != null ? s.getLastRunAt() : now.minusSeconds(60);
      java.time.ZonedDateTime next = cron.next(base.atZone(java.time.ZoneId.systemDefault()));
      if (next == null || next.isAfter(now.atZone(java.time.ZoneId.systemDefault()))) {
        continue;
      }
      runSchedule(s);
      s.setLastRunAt(now);
      scheduleRepository.save(s);
    }
  }

  private void runSchedule(RegistryScanSchedule s) {
    RegistryDatasource ds = registryDatasourceService.getById(s.getDatasourceId());
    try {
      if (StringUtils.hasText(s.getImageRef())) {
        trivyScanService.scanAndStore(
            s.getImageRef().trim(),
            ds.getUsername(),
            ds.getPassword(),
            "system",
            TrivyScanService.JOB_TYPE_CRON);
        return;
      }
      if (StringUtils.hasText(s.getRepoName())) {
        List<String> refs = registryDatasourceService.listImageRefs(ds.getId(), s.getRepoName().trim());
        TrivyScanService.ScanMetadata metadata = trivyScanService.buildBatchMetadata(refs);
        for (String ref : refs) {
          trivyScanService.scanAndStore(
              ref,
              ds.getUsername(),
              ds.getPassword(),
              metadata,
              "system",
              TrivyScanService.JOB_TYPE_CRON);
        }
      }
    } catch (Exception ignored) {
      // scheduler should continue processing other items
    }
  }

  private RegistryScanSchedule getById(Long id) {
    return scheduleRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found: " + id));
  }

  private void validateRequest(RegistryScanScheduleRequest request) {
    try {
      CronExpression.parse(request.getCronExpression());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression");
    }
    registryDatasourceService.getById(request.getDatasourceId());
    if (!StringUtils.hasText(request.getImageRef()) && !StringUtils.hasText(request.getRepoName())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either imageRef or repoName is required");
    }
  }

  private void applyRequest(RegistryScanSchedule s, RegistryScanScheduleRequest request) {
    s.setDatasourceId(request.getDatasourceId());
    s.setRepoName(StringUtils.hasText(request.getRepoName()) ? request.getRepoName().trim() : null);
    s.setImageRef(StringUtils.hasText(request.getImageRef()) ? request.getImageRef().trim() : null);
    s.setCronExpression(request.getCronExpression().trim());
    s.setEnabled(request.isEnabled());
  }

  private RegistryScanScheduleResponse toResponse(RegistryScanSchedule s) {
    RegistryDatasource ds = registryDatasourceService.getById(s.getDatasourceId());
    return new RegistryScanScheduleResponse(
        s.getId(),
        s.getDatasourceId(),
        ds.getName(),
        s.getRepoName(),
        s.getImageRef(),
        s.getCronExpression(),
        s.isEnabled(),
        s.getLastRunAt(),
        s.getCreateTime(),
        s.getUpdateTime());
  }
}
