package com.ligg.flowscheduler.archive.service;

import tools.jackson.databind.ObjectMapper;
import com.ligg.flowscheduler.archive.dto.ArchiveLatestDto;
import com.ligg.flowscheduler.archive.config.BangumiArchiveSyncProperties;
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
 * latest.json 和 Release 资源均先流式写入临时文件，避免响应体整包驻留内存。
 *
 * @author Ligg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiArchiveHttpService {

    private final BangumiArchiveSyncProperties properties;
    private final ObjectMapper objectMapper;

    public ArchiveLatestDto fetchLatest() throws IOException, InterruptedException {
        Path directory = Path.of(properties.getDownloadDir());
        Files.createDirectories(directory);
        Path latestFile = Files.createTempFile(directory, "archive-latest-", ".json");
        try {
            downloadToFile(properties.getLatestUrl(), latestFile, properties.getConnectTimeoutSeconds());
            // 只保留同步编排所需的元数据；原始响应体保存在文件中。
            return objectMapper.readValue(latestFile.toFile(), ArchiveLatestDto.class);
        } finally {
            deleteTempFile(latestFile);
        }
    }

    public void download(String url, Path target) throws IOException, InterruptedException {
        downloadToFile(url, target, properties.getReadTimeoutSeconds());
        log.info("Downloaded {} ({} bytes)", target.getFileName(), Files.size(target));
    }

    private void downloadToFile(String url, Path target, int timeoutSeconds)
            throws IOException, InterruptedException {
        target = target.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "archive-download-", ".part");
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Download failed: HTTP " + response.statusCode() + " url=" + url);
                }
                Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            // 完整下载后才替换目标文件，失败时不暴露半包数据。
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteTempFile(temp);
        }
    }

    private void deleteTempFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete archive temp file {}", file, e);
        }
    }

}
