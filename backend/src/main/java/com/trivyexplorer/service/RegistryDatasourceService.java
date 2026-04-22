package com.trivyexplorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivyexplorer.domain.RegistryDatasource;
import com.trivyexplorer.repo.RegistryDatasourceRepository;
import com.trivyexplorer.web.dto.RegistryDatasourceRequest;
import com.trivyexplorer.web.dto.RegistryDatasourceResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistryDatasourceService {
  private final RegistryDatasourceRepository registryDatasourceRepository;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public RegistryDatasourceService(
      RegistryDatasourceRepository registryDatasourceRepository, ObjectMapper objectMapper) {
    this.registryDatasourceRepository = registryDatasourceRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<RegistryDatasourceResponse> listDatasources() {
    return registryDatasourceRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public RegistryDatasource getById(Long id) {
    return registryDatasourceRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Datasource not found: " + id));
  }

  @Transactional
  public RegistryDatasourceResponse create(RegistryDatasourceRequest request) {
    RegistryDatasource ds = new RegistryDatasource();
    applyRequest(ds, request);
    return toResponse(registryDatasourceRepository.save(ds));
  }

  @Transactional
  public RegistryDatasourceResponse update(Long id, RegistryDatasourceRequest request) {
    RegistryDatasource ds = getById(id);
    applyRequest(ds, request);
    return toResponse(registryDatasourceRepository.save(ds));
  }

  @Transactional
  public void delete(Long id) {
    registryDatasourceRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<String> listRepos(Long datasourceId) {
    RegistryDatasource ds = getById(datasourceId);
    if (!"harbor".equals(resolveType(ds))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only harbor datasource supports repo listing");
    }
    JsonNode root = harborGet(ds, "/api/v2.0/repositories?page_size=100");
    List<String> repos = new ArrayList<>();
    if (root.isArray()) {
      for (JsonNode item : root) {
        String name = item.path("name").asText(null);
        if (StringUtils.hasText(name)) {
          repos.add(name.trim());
        }
      }
    }
    return repos;
  }

  @Transactional(readOnly = true)
  public List<String> listImageRefs(Long datasourceId, String repoName) {
    RegistryDatasource ds = getById(datasourceId);
    if (!"harbor".equals(resolveType(ds))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only harbor datasource supports image listing");
    }
    if (!StringUtils.hasText(repoName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repoName is required");
    }
    String encodedRepo = URLEncoder.encode(repoName.trim(), StandardCharsets.UTF_8);
    JsonNode root =
        harborGet(
            ds,
            "/api/v2.0/repositories/" + encodedRepo + "/artifacts?page_size=100&with_tag=true");

    Set<String> refs = new LinkedHashSet<>();
    if (root.isArray()) {
      for (JsonNode item : root) {
        JsonNode tags = item.path("tags");
        if (tags.isArray()) {
          for (JsonNode tag : tags) {
            String tagName = tag.path("name").asText(null);
            if (StringUtils.hasText(tagName)) {
              refs.add(ds.getHarborBaseUrl().replaceFirst("^https?://", "") + "/" + repoName + ":" + tagName);
            }
          }
        }
      }
    }
    return new ArrayList<>(refs);
  }

  private JsonNode harborGet(RegistryDatasource ds, String path) {
    String base = ds.getHarborBaseUrl().trim();
    String url = base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    String basic =
        Base64.getEncoder()
            .encodeToString((ds.getUsername() + ":" + ds.getPassword()).getBytes(StandardCharsets.UTF_8));
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Basic " + basic)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Harbor API call failed: " + res.statusCode());
      }
      return objectMapper.readTree(res.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Harbor API error: " + e.getMessage());
    }
  }

  private void applyRequest(RegistryDatasource ds, RegistryDatasourceRequest request) {
    String type = normalizeType(request.getType());
    ds.setName(request.getName().trim());
    ds.setType(type);
    if ("harbor".equals(type)) {
      if (!StringUtils.hasText(request.getHarborBaseUrl())
          || !StringUtils.hasText(request.getUsername())
          || !StringUtils.hasText(request.getPassword())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "harborBaseUrl, username, password are required for harbor type");
      }
      ds.setHarborBaseUrl(request.getHarborBaseUrl().trim());
      ds.setUsername(request.getUsername().trim());
      ds.setPassword(request.getPassword());
      return;
    }
    if (!StringUtils.hasText(request.getAk()) || !StringUtils.hasText(request.getSk())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ak and sk are required for swr type");
    }
    ds.setHarborBaseUrl("swr");
    ds.setUsername(request.getAk().trim());
    ds.setPassword(request.getSk());
  }

  private String normalizeType(String type) {
    String normalized = StringUtils.hasText(type) ? type.trim().toLowerCase() : "harbor";
    if (!"harbor".equals(normalized) && !"swr".equals(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported datasource type: " + type);
    }
    return normalized;
  }

  private String resolveType(RegistryDatasource ds) {
    return StringUtils.hasText(ds.getType()) ? ds.getType().trim().toLowerCase() : "harbor";
  }

  private RegistryDatasourceResponse toResponse(RegistryDatasource ds) {
    return new RegistryDatasourceResponse(
        ds.getId(),
        ds.getName(),
        resolveType(ds),
        ds.getHarborBaseUrl(),
        ds.getUsername(),
        ds.getCreateTime(),
        ds.getUpdateTime());
  }
}
