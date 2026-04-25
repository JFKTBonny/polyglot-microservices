package com.microservices.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedEvent {

    private String orderId;
    private String userId;
    private String status;
    private String reason;
}
