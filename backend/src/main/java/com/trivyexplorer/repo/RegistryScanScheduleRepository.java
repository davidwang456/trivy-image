package com.trivyexplorer.repo;

import com.trivyexplorer.domain.RegistryScanSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistryScanScheduleRepository extends JpaRepository<RegistryScanSchedule, Long> {
  List<RegistryScanSchedule> findByEnabledTrue();
}
