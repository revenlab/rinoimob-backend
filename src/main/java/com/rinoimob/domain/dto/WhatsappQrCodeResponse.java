package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappQrCodeResponse {
    private String pairingCode;
    private String code; // base64 QR
    private String status; // current instance status
}
