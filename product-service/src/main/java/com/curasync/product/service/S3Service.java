package com.curasync.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Set;
import java.util.UUID;

/**
 * Uploads product images and videos to S3 with a public-read ACL and
 * returns the public URL — replaces the Cloudinary upload path for
 * product-service (see ProductController's /upload endpoint).
 *
 * Bucket must have public reads enabled (either via this ACL, or a bucket
 * policy if the bucket has "Block all public access" / ACLs disabled —
 * see the setup notes in the final summary for exact S3 console steps).
 */
@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm", "video/x-matroska");

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;  // 200 MB

    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public boolean isConfigured() {
        return bucket != null && !bucket.isBlank();
    }

    public boolean isImage(String contentType) {
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    public boolean isVideo(String contentType) {
        return contentType != null && VIDEO_CONTENT_TYPES.contains(contentType.toLowerCase());
    }

    /**
     * Uploads a file to S3 under {@code folder/<uuid><extension>} with a
     * public-read ACL and returns its public URL. Validates content type
     * and size against the appropriate limit for images vs video.
     */
    public String upload(byte[] bytes, String contentType, String originalFilename, String folder) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "S3 isn't configured — set AWS_S3_BUCKET (and AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY if not using an IAM role).");
        }

        boolean image = isImage(contentType);
        boolean video = isVideo(contentType);

        if (!image && !video) {
            throw new IllegalStateException(
                    "Unsupported file type: " + contentType + ". Allowed: jpg, png, webp, gif, mp4, mov, webm, mkv.");
        }

        long maxBytes = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (bytes.length > maxBytes) {
            throw new IllegalStateException(
                    "File too large: " + (bytes.length / (1024 * 1024)) + "MB. Max allowed: " + (maxBytes / (1024 * 1024)) + "MB.");
        }

        String extension = extractExtension(originalFilename, contentType);
        String key = folder + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ) // bucket must allow ACLs for this; see setup notes
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            String url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
            log.info("Uploaded {} to S3: key={}, size={}KB, url={}",
                    video ? "video" : "image", key, bytes.length / 1024, url);
            return url;

        } catch (S3Exception ex) {
            log.error("S3 upload failed for key={}: {}", key, ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage(), ex);
            throw new IllegalStateException("Failed to upload to S3: " + ex.getMessage(), ex);
        }
    }

    private String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        // Fall back to a reasonable extension from content type
        return switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            case "video/x-matroska" -> ".mkv";
            default -> "";
        };
    }
}
