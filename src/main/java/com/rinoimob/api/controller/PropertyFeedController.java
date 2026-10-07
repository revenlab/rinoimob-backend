package com.rinoimob.api.controller;

import com.rinoimob.service.integration.GrupoOlxPropertyFeedService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class PropertyFeedController {
    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final GrupoOlxPropertyFeedService feedService;

    @GetMapping(value = "/grupo-olx/{outboundKey}.xml", produces = "application/xml;charset=UTF-8")
    @Operation(summary = "Publish the tenant property inventory as a Grupo OLX VRSync feed")
    public ResponseEntity<byte[]> grupoOlx(@PathVariable String outboundKey) {
        return ResponseEntity.ok()
                .contentType(XML_UTF8)
                .cacheControl(CacheControl.noStore())
                .body(feedService.generate(outboundKey));
    }
}
