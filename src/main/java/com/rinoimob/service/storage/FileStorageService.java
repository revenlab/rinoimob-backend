package com.rinoimob.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class FileStorageService {

    private final RestTemplate restTemplate;
    private final String masterUrl;

    public FileStorageService(
            RestTemplate restTemplate,
            @Value("${seaweedfs.master.url:http://localhost:9333}") String masterUrl) {
        this.restTemplate = restTemplate;
        this.masterUrl = masterUrl;
    }

    public UploadResult upload(MultipartFile file) {
        AssignResult assign = assign();
        String uploadUrl = "http://" + assign.url() + "/" + assign.fid();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartFileResource(file));
        } catch (IOException e) {
            throw new FileStorageException("Failed to read upload file: " + e.getMessage(), e);
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.put(uploadUrl, request);

        log.debug("Uploaded file to SeaweedFS fid={} url={}", assign.fid(), uploadUrl);
        return new UploadResult(assign.fid(), uploadUrl);
    }

    public void delete(String seaweedFid, String url) {
        try {
            restTemplate.delete(url);
            log.debug("Deleted file from SeaweedFS fid={}", seaweedFid);
        } catch (Exception e) {
            // Log but don't fail — the DB record will still be deleted
            log.warn("Failed to delete file from SeaweedFS fid={}: {}", seaweedFid, e.getMessage());
        }
    }

    private AssignResult assign() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(
                masterUrl + "/dir/assign", Map.class);

        if (response == null || !response.containsKey("fid")) {
            throw new FileStorageException("SeaweedFS assign returned no fid");
        }

        String fid = (String) response.get("fid");
        String url = (String) response.get("url");
        return new AssignResult(fid, url);
    }

    public record AssignResult(String fid, String url) {}
    public record UploadResult(String fid, String url) {}

    public static class FileStorageException extends RuntimeException {
        public FileStorageException(String message) {
            super(message);
        }
        public FileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
