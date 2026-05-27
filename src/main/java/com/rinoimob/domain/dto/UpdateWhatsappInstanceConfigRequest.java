package com.rinoimob.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWhatsappInstanceConfigRequest {
    private Boolean autoCreateLeadsFromUnknownNumbers;
    private UUID assignedToUserId;
}
