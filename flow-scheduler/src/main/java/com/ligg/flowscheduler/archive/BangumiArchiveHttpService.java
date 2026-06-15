package com.ligg.flowscheduler.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.flowscheduler.archive.dto.ArchiveLatestDto;
import com.ligg.flowscheduler.config.BangumiArchiveSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 负责请求 latest.json 及将 Release 资源流式下载到本地临时目录。
 *
 * @author Ligg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiArchiveHttpService {

    private final BangumiArchiveSyncProperties properties;
    private final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    public ArchiveLatestDto fetchLatest() throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(properties.getLatestUrl()))
                        .GET()
                        .timeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch latest.json: HTTP " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            return objectMapper.readValue(body, ArchiveLatestDto.class);
        }
    }

    public void download(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        HttpResponse<InputStream> response = httpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode() + " url=" + url);
        }
        try (InputStream body = response.body()) {
            Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Downloaded {} ({} bytes)", target.getFileName(), Files.size(target));
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return httpClient;
    }
}
