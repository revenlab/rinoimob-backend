package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendWhatsappActionConfigDto {
    private String instanceId;           // UUID da instância WhatsApp
    private String recipientType;        // LEAD, ASSIGNED_USER, CUSTOM_NUMBER
    private String recipientValue;       // Para CUSTOM_NUMBER, o número de telefone
    private String message;              // Mensagem customizada
    private String messageTemplate;      // Template de mensagem com variáveis
}
