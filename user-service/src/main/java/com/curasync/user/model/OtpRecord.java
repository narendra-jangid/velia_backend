package com.curasync.user.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "otp_records")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OtpRecord {

    @Id
    private String id;

    @Indexed(unique = true)
    private String phone;

    private String otp;
    private Instant expiresAt;
    private int attempts;

}
