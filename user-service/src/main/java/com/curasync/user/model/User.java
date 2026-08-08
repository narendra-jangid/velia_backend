package com.curasync.user.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Customer profile, keyed by phone number. Created (or fetched) the moment
 * OTP verification succeeds in the Next.js app, then kept up to date as the
 * customer edits their details during checkout — so next time they log in,
 * the checkout form and order history are ready to go.
 */
@Document(collection = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String phone;

    private String name;
    private String email;

    // Last-used shipping address — prefills the checkout form next time
    private String address;
    private String city;
    private String state;
    private String pincode;

    /** Persisted shopping cart — synced from the Velia frontend per logged-in user. */
    @Builder.Default
    private List<CartItem> cart = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
