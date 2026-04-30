package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateWhatsappInstanceRequest {
    private String displayName;
    private String phoneNumber; // optional at creation time
}
