package com.trivyexplorer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trivyexplorer.domain.RegistryDatasource;
import com.trivyexplorer.repo.RegistryDatasourceRepository;
import com.trivyexplorer.web.dto.ScanResponse;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SwrService {
  private static final DateTimeFormatter SWR_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final int SCAN_THREAD_POOL_SIZE = 20;
  private static final HashMap<Long, SwrClient> SWR_CLIENT_MAP = new HashMap<>();

  private final RegistryDatasourceService registryDatasourceService;
  private final RegistryDatasourceRepository registryDatasourceRepository;
  private final TrivyScanService trivyScanService;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public SwrService(
      RegistryDatasourceService registryDatasourceService,
      RegistryDatasourceRepository registryDatasourceRepository,
      TrivyScanService trivyScanService,
      ObjectMapper objectMapper) {
    this.registryDatasourceService = registryDatasourceService;
    this.registryDatasourceRepository = registryDatasourceRepository;
    this.trivyScanService = trivyScanService;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  public void initSwrClients() {
    List<RegistryDatasource> datasources = registryDatasourceRepository.findAll();
    synchronized (SWR_CLIENT_MAP) {
      SWR_CLIENT_MAP.clear();
      for (RegistryDatasource ds : datasources) {
        String type = StringUtils.hasText(ds.getType()) ? ds.getType().trim().toLowerCase() : "harbor";
        if (!"swr".equals(type)) {
          continue;
        }
        SWR_CLIENT_MAP.put(ds.getId(), buildSwrClient(ds));
      }
    }
  }

  @Transactional(readOnly = true)
  public List<String> listNamespaces(Long datasourceId) {
    SwrClient client = getOrBuildSwrClient(datasourceId);
    JsonNode root = swrGet(client, "/v2/manage/namespaces?limit=1000");
    Set<String> namespaces = new LinkedHashSet<>();
    JsonNode namespaceItems = root.path("namespaces");
    if (namespaceItems.isArray()) {
      for (JsonNode item : namespaceItems) {
        String name = item.path("name").asText(null);
        if (StringUtils.hasText(name)) {
          namespaces.add(name.trim());
        }
      }
    } else {
      collectStringFields(root, namespaces, "name", "namespace", "namespace_name");
    }
    return namespaces.stream().sorted().toList();
  }

  @Transactional(readOnly = true)
  public List<String> listRepos(Long datasourceId, String namespace) {
    SwrClient client = getOrBuildSwrClient(datasourceId);
    Set<String> repos = new LinkedHashSet<>();
    String trimmedNamespace = StringUtils.hasText(namespace) ? namespace.trim() : null;
    String path =
        trimmedNamespace == null
            ? "/v2/manage/repos?limit=1000"
            : "/v2/manage/repos?namespace=" + urlEncode(trimmedNamespace) + "&limit=1000";
    JsonNode root = swrGet(client, path);

    if (root.isArray()) {
      for (JsonNode item : root) {
        String repoName = item.path("name").asText(null);
        if (!StringUtils.hasText(repoName)) {
          continue;
        }
        String ns = item.path("namespace").asText(null);
        if (trimmedNamespace == null && StringUtils.hasText(ns)) {
          repos.add(ns.trim() + "/" + repoName.trim());
        } else {
          repos.add(repoName.trim());
        }
      }
      return repos.stream().sorted().toList();
    }

    collectStringFields(root, repos, "name", "repo_name", "repository", "path");
    return repos.stream().sorted().toList();
  }

  @Transactional(readOnly = true)
  public List<String> listImageRefs(Long datasourceId, String namespace, String repoName) {
    SwrClient client = getOrBuildSwrClient(datasourceId);
    if (!StringUtils.hasText(namespace)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "namespace is required");
    }
    if (!StringUtils.hasText(repoName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repoName is required");
    }
    JsonNode root =
        swrGet(
            client,
            "/v2/manage/namespaces/"
                + urlEncode(namespace.trim())
                + "/repos/"
                + urlEncode(repoName.trim())
                + "/tags?limit=1000");
    Set<String> tags = new LinkedHashSet<>();
    collectStringFields(root, tags, "name", "tag", "tag_name");
    String host = client.baseUrl().replaceFirst("^https?://", "").replaceAll("/+$", "");
    return tags.stream()
        .sorted(Comparator.naturalOrder())
        .map(tag -> host + "/" + namespace.trim() + "/" + repoName.trim() + ":" + tag)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<String> listImageRefsByNamespace(Long datasourceId, String namespace) {
    if (!StringUtils.hasText(namespace)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "namespace is required");
    }
    Set<String> refs = new LinkedHashSet<>();
    for (String repo : listRepos(datasourceId, namespace.trim())) {
      refs.addAll(listImageRefs(datasourceId, namespace.trim(), repo));
    }
    return refs.stream().sorted().toList();
  }

  public List<ScanResponse> scanNamespace(Long datasourceId, String namespace, String operator) {
    List<String> imageRefs = listImageRefsByNamespace(datasourceId, namespace);
    if (imageRefs.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No images found in namespace");
    }
    SwrClient client = getOrBuildSwrClient(datasourceId);
    return scanImagesConcurrently(imageRefs, client, operator);
  }

  public List<ScanResponse> scanRepo(
      Long datasourceId, String namespace, String repoName, String operator) {
    List<String> imageRefs = listImageRefs(datasourceId, namespace, repoName);
    if (imageRefs.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No images found in repo");
    }
    SwrClient client = getOrBuildSwrClient(datasourceId);
    return scanImagesConcurrently(imageRefs, client, operator);
  }

  private List<ScanResponse> scanImagesConcurrently(
      List<String> imageRefs, SwrClient client, String operator) {
    String sharedJobId = UUID.randomUUID().toString();
    ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREAD_POOL_SIZE);
    try {
      List<CompletableFuture<ScanResponse>> tasks =
          imageRefs.stream()
              .map(
                  imageRef ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            TrivyScanService.ImageHierarchy hierarchy =
                                TrivyScanService.resolveHierarchy(imageRef);
                            TrivyScanService.ScanMetadata metadata =
                                new TrivyScanService.ScanMetadata(
                                    sharedJobId,
                                    hierarchy.systemName() + "/" + hierarchy.projectName(),
                                    hierarchy.systemName(),
                                    hierarchy.projectName());
                            try {
                              return trivyScanService.scanAndStore(
                                  imageRef,
                                  client.ak(),
                                  client.sk(),
                                  metadata,
                                  operator,
                                  TrivyScanService.JOB_TYPE_MANUAL);
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new CompletionException(e);
                            } catch (IOException e) {
                              throw new CompletionException(e);
                            }
                          },
                          executor))
              .toList();
      return tasks.stream().map(CompletableFuture::join).toList();
    } catch (CompletionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      String message = cause.getMessage() == null ? "Scan failed" : cause.getMessage();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    } finally {
      executor.shutdown();
    }
  }

  private RegistryDatasource requireSwrDatasource(Long datasourceId) {
    RegistryDatasource ds = registryDatasourceService.getById(datasourceId);
    String type = StringUtils.hasText(ds.getType()) ? ds.getType().trim().toLowerCase() : "harbor";
    if (!"swr".equals(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only swr datasource supports this operation");
    }
    return ds;
  }

  private SwrClient getOrBuildSwrClient(Long datasourceId) {
    synchronized (SWR_CLIENT_MAP) {
      SwrClient cached = SWR_CLIENT_MAP.get(datasourceId);
      if (cached != null) {
        return cached;
      }
      RegistryDatasource ds = requireSwrDatasource(datasourceId);
      SwrClient built = buildSwrClient(ds);
      SWR_CLIENT_MAP.put(datasourceId, built);
      return built;
    }
  }

  private static SwrClient buildSwrClient(RegistryDatasource ds) {
    return new SwrClient(
        ds.getId(),
        ds.getHarborBaseUrl().trim().replaceAll("/+$", ""),
        ds.getUsername().trim(),
        ds.getPassword());
  }

  private JsonNode swrGet(SwrClient client, String path) {
    String base = client.baseUrl();
    String url = base + path;
    String host = URI.create(base).getHost();
    String xSdkDate = SWR_TIME_FORMAT.format(Instant.now());
    String auth = buildSwrAuthorization("GET", path, host, xSdkDate, client.ak(), client.sk());
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Host", host)
              .header("X-Sdk-Date", xSdkDate)
              .header("Authorization", auth)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "SWR API call failed: " + res.statusCode() + " " + res.body());
      }
      return objectMapper.readTree(res.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SWR API error: " + e.getMessage());
    }
  }

  private static String buildSwrAuthorization(
      String method, String path, String host, String xSdkDate, String ak, String sk) {
    String signedHeaders = "host;x-sdk-date";
    String canonicalHeaders = "host:" + host.toLowerCase(Locale.ROOT) + "\n" + "x-sdk-date:" + xSdkDate + "\n";
    String canonicalRequest =
        method
            + "\n"
            + path
            + "\n"
            + "\n"
            + canonicalHeaders
            + "\n"
            + signedHeaders
            + "\n"
            + sha256Hex("");
    String stringToSign = "SDK-HMAC-SHA256\n" + xSdkDate + "\n" + sha256Hex(canonicalRequest);
    String signature = hmacSha256Hex(sk, stringToSign);
    return "SDK-HMAC-SHA256 Access="
        + ak
        + ", SignedHeaders="
        + signedHeaders
        + ", Signature="
        + signature;
  }

  private static String hmacSha256Hex(String key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return toHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build SWR signature", e);
    }
  }

  private static String sha256Hex(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return toHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash request", e);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static void collectStringFields(JsonNode node, Set<String> out, String... keys) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        collectStringFields(item, out, keys);
      }
      return;
    }
    if (node.isObject()) {
      for (String key : keys) {
        String value = node.path(key).asText(null);
        if (StringUtils.hasText(value)) {
          out.add(value.trim());
        }
      }
      node.fields().forEachRemaining(entry -> collectStringFields(entry.getValue(), out, keys));
    }
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record SwrClient(Long datasourceId, String baseUrl, String ak, String sk) {}
}
