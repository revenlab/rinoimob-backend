package com.rinoimob.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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
    private final String volumePublicUrl;

    public FileStorageService(
            RestTemplate restTemplate,
            @Value("${seaweedfs.master.url:http://localhost:9333}") String masterUrl,
            @Value("${seaweedfs.volume.public-url:http://localhost:8080}") String volumePublicUrl) {
        this.restTemplate = restTemplate;
        this.masterUrl = masterUrl;
        this.volumePublicUrl = volumePublicUrl;
    }

    public UploadResult upload(MultipartFile file) {
        AssignResult assign = assign();

        // Upload URL uses internal address (Docker network); public URL is used for serving.
        String uploadUrl = "http://" + assign.url() + "/" + assign.fid();
        String publicUrl = volumePublicUrl + "/" + assign.fid();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new MultipartFileResource(file));
        } catch (IOException e) {
            throw new FileStorageException("Failed to read upload file: " + e.getMessage(), e);
        }

        // SeaweedFS requires POST for multipart/form-data uploads.
        // PUT expects raw bytes; sending multipart via PUT corrupts the stored file.
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(uploadUrl, HttpMethod.POST, request, String.class);

        log.debug("Uploaded file to SeaweedFS fid={} url={}", assign.fid(), publicUrl);
        return new UploadResult(assign.fid(), publicUrl);
    }

    public void delete(String seaweedFid, String url) {
        try {
            // Resolve deletion via internal URL derived from fid
            String internalUrl = resolveInternalUrl(url, seaweedFid);
            restTemplate.delete(internalUrl);
            log.debug("Deleted file from SeaweedFS fid={}", seaweedFid);
        } catch (Exception e) {
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

    /**
     * Builds internal deletion URL. Stored URLs use the public base URL,
     * so we reconstruct the internal address from the master assign result.
     * If the volume server is local (url == publicUrl host), use it directly.
     */
    private String resolveInternalUrl(String storedUrl, String fid) {
        // Strip the public base and reattach to internal master-assigned URL.
        // Simplest: just re-assign to get a fresh volume url for the same fid via lookup.
        // SeaweedFS supports DELETE directly on the volume node.
        // We lookup the fid location via the master.
        @SuppressWarnings("unchecked")
        Map<String, Object> lookup = restTemplate.getForObject(
                masterUrl + "/dir/lookup?volumeId=" + fid.split(",")[0], Map.class);
        if (lookup != null && lookup.containsKey("locations")) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> locations =
                    (java.util.List<Map<String, Object>>) lookup.get("locations");
            if (!locations.isEmpty()) {
                String internalHost = (String) locations.get(0).get("url");
                return "http://" + internalHost + "/" + fid;
            }
        }
        // Fallback: swap public base with internal equivalent
        return storedUrl.replace(volumePublicUrl, masterUrl.replace("9333", "8080"));
    }

    public record AssignResult(String fid, String url) {}
    public record UploadResult(String fid, String url) {}

    public static class FileStorageException extends RuntimeException {
        public FileStorageException(String message) { super(message); }
        public FileStorageException(String message, Throwable cause) { super(message, cause); }
    }
}

