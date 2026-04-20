package com.trivyexplorer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "image_scans")
public class ImageScan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 2048)
  private String imageRef;

  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(nullable = false, columnDefinition = "BYTEA")
  private byte[] reportJson;

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

  public String getImageRef() {
    return imageRef;
  }

  public void setImageRef(String imageRef) {
    this.imageRef = imageRef;
  }

  public byte[] getReportJson() {
    return reportJson;
  }

  public void setReportJson(byte[] reportJson) {
    this.reportJson = reportJson;
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
