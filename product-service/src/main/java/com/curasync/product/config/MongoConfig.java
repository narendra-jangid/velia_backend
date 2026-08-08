package com.curasync.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Enables @CreatedDate and @LastModifiedDate on the Product document.
 * Equivalent to the Mongoose { timestamps: true } schema option.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
