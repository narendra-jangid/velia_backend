package com.curasync.product.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;


@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    private String name;
    private String slug;
    private String category;
    private String brand;
    private String description;

    private Double price;
    private Double mrp;
    private Integer stock = 0;

    private String fabric;
    private String fit;
    private String pattern;
    private String length;
    private String neckType;
    private String sleeve;
    private String occasion;

    private String washCare;
    private String whatsIncluded;
    private String weight;
    private String height;

    private Boolean liningAvailable = false;
    private Boolean featured = false;
    private Boolean active = true;

    private List<SizeEntry> sizes;
    private List<String> colors;

    private String thumbnail;
    private List<String> images;

    // Single product video (S3 public URL) — shown in the gallery on the
    // product detail page alongside the images, apart from the images list.
    private String video;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SizeEntry {
        private String size;
        private String dimension = "";
    }

}
