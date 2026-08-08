package com.curasync.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Bean
    public S3Client s3Client() {

        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(
                                            accessKey,
                                            secretKey
                                    )
                            )
                    )
                    .httpClientBuilder(
                            UrlConnectionHttpClient.builder()
                    )
                    .build();
        }

        return S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                )
                .build();
    }
}
