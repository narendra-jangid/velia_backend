package com.curasync.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.curasync.product.exception.ProductNotFoundException;
import com.curasync.product.model.Product;
import com.curasync.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class ProductService {

    private final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final Cloudinary        cloudinary;
    private final S3Service         s3Service;

    public ProductService(ProductRepository productRepository, Cloudinary cloudinary, S3Service s3Service) {
        this.productRepository = productRepository;
        this.cloudinary = cloudinary;
        this.s3Service = s3Service;
    }

    // ── READ ──────────────────────────────────────────────────────

    public List<Product> getAllProducts() {
        return productRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Product getProductById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException("slug:" + slug));
    }

    public List<Product> getByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> getFeaturedProducts() {
        return productRepository.findByFeaturedTrue();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByActiveTrue();
    }

    // ── CREATE ────────────────────────────────────────────────────

    public Product createProduct(Map<String, Object> request) {
        Product product = new Product();
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        mapToProduct(product, request);
        Product saved = productRepository.save(product);
        log.info("Product created: {}", saved.getName());
        return saved;
    }

    // ── UPDATE ────────────────────────────────────────────────────

    public Product updateProduct(String id, Map<String, Object> request) {
        Product existing = getProductById(id);
        mapToProduct(existing, request);
        existing.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(existing);
        log.info("Product updated: {}", id);
        return saved;
    }

    // ── DELETE ────────────────────────────────────────────────────

    public void deleteProduct(String id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
        log.info("Product deleted: {}", id);
    }

    // ── STOCK REDUCTION (called by Order Service via Feign) ───────

    public void reduceStock(String id, Integer quantity) {
        Product product = getProductById(id);
        if (product.getStock() < quantity) {
            log.warn("Stock reduction rejected for product {}: available={}, requested={}", id, product.getStock(), quantity);
            throw new IllegalStateException(
                    "Insufficient stock for '" + product.getName() +
                            "'. Available: " + product.getStock() + ", Requested: " + quantity);
        }
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        log.info("Stock reduced for product {}: -{}", id, quantity);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────

    /**
     * Maps a raw Map<String, Object> (from @RequestBody JSON) onto a
     * Product document. Each field is only set if the key is present in
     * the map, so partial updates work the same as full ones.
     */
    private void mapToProduct(Product product, Map<String, Object> map) {
        if (map.containsKey("name"))            product.setName(str(map, "name"));
        if (map.containsKey("slug"))            product.setSlug(str(map, "slug"));
        if (map.containsKey("category"))        product.setCategory(str(map, "category"));
        if (map.containsKey("brand"))           product.setBrand(str(map, "brand"));
        if (map.containsKey("description"))     product.setDescription(str(map, "description"));
        if (map.containsKey("fabric"))          product.setFabric(str(map, "fabric"));
        if (map.containsKey("fit"))             product.setFit(str(map, "fit"));
        if (map.containsKey("pattern"))         product.setPattern(str(map, "pattern"));
        if (map.containsKey("length"))          product.setLength(str(map, "length"));
        if (map.containsKey("neckType"))        product.setNeckType(str(map, "neckType"));
        if (map.containsKey("sleeve"))          product.setSleeve(str(map, "sleeve"));
        if (map.containsKey("occasion"))        product.setOccasion(str(map, "occasion"));
        if (map.containsKey("washCare"))        product.setWashCare(str(map, "washCare"));
        if (map.containsKey("whatsIncluded"))   product.setWhatsIncluded(str(map, "whatsIncluded"));
        if (map.containsKey("weight"))          product.setWeight(str(map, "weight"));
        if (map.containsKey("height"))          product.setHeight(str(map, "height"));
        if (map.containsKey("thumbnail"))       product.setThumbnail(str(map, "thumbnail"));
        if (map.containsKey("video"))           product.setVideo(str(map, "video"));

        if (map.containsKey("price"))
            product.setPrice(toDouble(map.get("price")));

        if (map.containsKey("mrp"))
            product.setMrp(toDouble(map.get("mrp")));

        if (map.containsKey("stock"))
            product.setStock(toInt(map.get("stock")));

        if (map.containsKey("liningAvailable"))
            product.setLiningAvailable(toBool(map.get("liningAvailable")));

        if (map.containsKey("featured"))
            product.setFeatured(toBool(map.get("featured")));

        if (map.containsKey("active"))
            product.setActive(toBool(map.get("active")));

        // colors: ["red", "blue"]
        if (map.containsKey("colors")) {
            Object raw = map.get("colors");
            if (raw instanceof List<?> list) {
                product.setColors(list.stream().map(Object::toString).toList());
            }
        }

        // sizes: [{"size": "M", "dimension": "38"}, ...]
        if (map.containsKey("sizes")) {
            Object raw = map.get("sizes");
            if (raw instanceof List<?> list) {
                List<Product.SizeEntry> sizes = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> sizeMap) {
                        Object sizeObj = sizeMap.get("size");
                        Object dimObj  = sizeMap.get("dimension");
                        String size = sizeObj != null ? sizeObj.toString() : null;
                        String dim  = dimObj  != null ? dimObj.toString()  : "";
                        if (size != null) {
                            sizes.add(new Product.SizeEntry(size, dim));
                        }
                    }
                }
                product.setSizes(sizes);
            }
        }

        // images: ["https://...", "https://..."]
        if (map.containsKey("images")) {
            Object raw = map.get("images");
            if (raw instanceof List<?> list) {
                product.setImages(list.stream().map(Object::toString).toList());
            }
        }
    }

    // ── TYPE COERCION UTILS ───────────────────────────────────────
    // Jackson deserialises JSON numbers as Integer or Double depending
    // on whether they have a decimal point, so we normalise here.

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private Double toDouble(Object v) {
        if (v == null)           return null;
        if (v instanceof Double) return (Double) v;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object v) {
        if (v == null)            return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number)  return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Boolean toBool(Object v) {
        if (v == null)            return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    // ── S3 UPLOAD (images + video — replaces the Cloudinary path below) ──

    public String uploadMedia(byte[] bytes, String contentType, String originalFilename, String folder) {
        log.info("Uploading media to S3: contentType={}, folder={}, size={}KB", contentType, folder, bytes.length / 1024);
        return s3Service.upload(bytes, contentType, originalFilename, folder);
    }

    // ── CLOUDINARY UPLOAD (superseded by S3 above as of the image+video S3
    // migration — kept in place, not called from ProductController anymore.
    // Cloudinary env vars/bean can be removed once you've confirmed nothing
    // else depends on this path.) ──

    @SuppressWarnings("unchecked")
    public String uploadToCloudinary(byte[] bytes, String folder) throws IOException {
        Map<String, Object> result = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                        "folder",        folder,
                        "resource_type", "image",
                        "quality",       "auto",
                        "fetch_format",  "auto"
                )
        );
        return (String) result.get("secure_url");
    }
}