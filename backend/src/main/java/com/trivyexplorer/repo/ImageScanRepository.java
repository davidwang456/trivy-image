package com.trivyexplorer.repo;

import com.trivyexplorer.domain.ImageScan;
import com.trivyexplorer.web.dto.ImageScanSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageScanRepository extends JpaRepository<ImageScan, Long> {

  Optional<ImageScan> findByImageRef(String imageRef);

  @Query(
      "SELECT new com.trivyexplorer.web.dto.ImageScanSummary(i.id, i.imageRef, i.jobId, i.jobName,"
          + " i.systemName, i.projectName, i.jobType, i.createBy, i.updateBy, i.createTime,"
          + " i.updateTime)"
          + " FROM ImageScan i ORDER BY i.createTime DESC")
  List<ImageScanSummary> findAllSummaries(Pageable pageable);

  @Query(
      "SELECT new com.trivyexplorer.web.dto.ImageScanSummary(i.id, i.imageRef, i.jobId, i.jobName,"
          + " i.systemName, i.projectName, i.jobType, i.createBy, i.updateBy, i.createTime,"
          + " i.updateTime)"
          + " FROM ImageScan i WHERE LOWER(i.imageRef) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY"
          + " i.createTime DESC")
  List<ImageScanSummary> findSummariesByImageRef(@Param("q") String q, Pageable pageable);

  List<ImageScan> findAllByOrderByCreateTimeDesc();

  @Query("SELECT COUNT(DISTINCT i.jobId) FROM ImageScan i")
  long countDistinctJobIds();

  @Query("SELECT COUNT(i) FROM ImageScan i")
  long countAllScans();

  @Query(
      value =
          "SELECT CAST(i.create_time AS date) AS stat_day,"
              + " COUNT(DISTINCT i.job_id) AS job_total,"
              + " COUNT(*) AS image_ref_total"
              + " FROM image_scans i"
              + " WHERE i.create_time >= :start"
              + " GROUP BY CAST(i.create_time AS date)"
              + " ORDER BY stat_day",
      nativeQuery = true)
  List<Object[]> aggregateDailySince(@Param("start") Instant start);
}
