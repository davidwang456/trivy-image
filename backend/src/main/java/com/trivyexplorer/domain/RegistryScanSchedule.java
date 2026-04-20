package com.trivyexplorer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "registry_scan_schedules")
public class RegistryScanSchedule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long datasourceId;

  @Column(length = 512)
  private String repoName;

  @Column(length = 2048)
  private String imageRef;

  @Column(nullable = false, length = 128)
  private String cronExpression;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column
  private Instant lastRunAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createTime;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getDatasourceId() {
    return datasourceId;
  }

  public void setDatasourceId(Long datasourceId) {
    this.datasourceId = datasourceId;
  }

  public String getRepoName() {
    return repoName;
  }

  public void setRepoName(String repoName) {
    this.repoName = repoName;
  }

  public String getImageRef() {
    return imageRef;
  }

  public void setImageRef(String imageRef) {
    this.imageRef = imageRef;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(Instant lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public Instant getCreateTime() {
    return createTime;
  }

  public void setCreateTime(Instant createTime) {
    this.createTime = createTime;
  }

  public Instant getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(Instant updateTime) {
    this.updateTime = updateTime;
  }
}
