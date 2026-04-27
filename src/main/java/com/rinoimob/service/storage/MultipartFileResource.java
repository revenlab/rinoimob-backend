package com.rinoimob.service.storage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Wraps a MultipartFile as a Spring Resource so RestTemplate can send it as multipart. */
class MultipartFileResource extends ByteArrayResource {

    private final String filename;

    MultipartFileResource(MultipartFile file) throws IOException {
        super(file.getBytes());
        this.filename = file.getOriginalFilename();
    }

    @Override
    public String getFilename() {
        return filename;
    }
}
