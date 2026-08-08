package com.curasync.user.model;

import lombok.*;

/**
 * A line item in the customer's persisted cart (stored on the User document).
 * Shape matches the Velia frontend cart and checkout payload.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CartItem {

    private String productId;
    private String name;
    private String img;
    private Double price;
    private Integer qty;
    private String size;
    private String color;

}
