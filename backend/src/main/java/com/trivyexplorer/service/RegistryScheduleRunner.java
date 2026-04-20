package com.trivyexplorer.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class RegistryScheduleRunner {
  private final RegistryScanScheduleService scheduleService;

  public RegistryScheduleRunner(RegistryScanScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @Scheduled(fixedDelay = 60000)
  public void run() {
    scheduleService.runDueSchedules();
  }
}
