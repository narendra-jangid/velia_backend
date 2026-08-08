package com.curasync.order.model;

import lombok.*;

/**
 * A single line item within an Order — one product + chosen size/color + qty.
 * Snapshotted at order time so historical orders stay accurate even if the
 * product is later edited or deleted.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderItem {
    private String productId;
    private String name;
    private String img;
    private Double price;
    private Integer qty;
    private String size;
    private String color;
}
