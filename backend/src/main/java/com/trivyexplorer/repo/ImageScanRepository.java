package com.trivyexplorer.repo;

import com.trivyexplorer.domain.ImageScan;
import com.trivyexplorer.web.dto.ImageScanSummary;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageScanRepository extends JpaRepository<ImageScan, Long> {

  @Query(
      "SELECT new com.trivyexplorer.web.dto.ImageScanSummary(i.id, i.imageRef, i.createTime,"
          + " i.updateTime) FROM ImageScan i ORDER BY i.createTime DESC")
  List<ImageScanSummary> findAllSummaries(Pageable pageable);

  @Query(
      "SELECT new com.trivyexplorer.web.dto.ImageScanSummary(i.id, i.imageRef, i.createTime,"
          + " i.updateTime) FROM ImageScan i WHERE LOWER(i.imageRef) LIKE LOWER(CONCAT('%', :q,"
          + " '%')) ORDER BY i.createTime DESC")
  List<ImageScanSummary> findSummariesByImageRef(@Param("q") String q, Pageable pageable);
}
