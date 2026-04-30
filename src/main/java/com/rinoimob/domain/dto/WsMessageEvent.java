package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WsMessageEvent {
    private String type;        // "WHATSAPP_MESSAGE" | "NOTIFICATION"
    private Object payload;
}
