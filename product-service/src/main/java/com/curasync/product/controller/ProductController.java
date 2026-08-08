package com.curasync.product.controller;

import com.curasync.product.model.Product;
import com.curasync.product.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    ProductController(ProductService ser){
        this.productService = ser;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    // Static paths MUST come before /{id} to avoid "featured" being treated as an id
    @GetMapping("/featured")
    public ResponseEntity<List<Product>> getFeaturedProducts() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<Product> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductBySlug(slug));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getByCategory(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Map<String, Object> request) {
        Product created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable String id, @RequestBody Map<String, Object> request) {
        Product updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok("Product deleted successfully.");
    }

    @PostMapping("/{id}/reduce-stock")
    public ResponseEntity<String> reduceStock(@PathVariable String id, @RequestBody Map<String, Object> request) {
        Object qtyObj = request.get("quantity");
        if (qtyObj == null) {
            return ResponseEntity.badRequest().body("quantity is required.");
        }
        Integer quantity = Integer.parseInt(qtyObj.toString());
        productService.reduceStock(id, quantity);
        return ResponseEntity.ok("Stock updated successfully.");
    }

    // Handles both product images and the new product video — media type is
    // detected from the file's content-type (falls back to the "type" param
    // if the browser doesn't send one). S3Service validates type/size and
    // returns a public URL immediately usable in Product.thumbnail/images/video.
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "velia/products") String folder,
            @RequestParam(value = "type", required = false) String typeHint
    ) throws IOException {
        String url = productService.uploadMedia(file.getBytes(), file.getContentType(), file.getOriginalFilename(), folder);
        String mediaType = "video".equalsIgnoreCase(typeHint) ? "video"
                : (file.getContentType() != null && file.getContentType().startsWith("video/")) ? "video" : "image";
        return ResponseEntity.ok(Map.of("url", url, "type", mediaType));
    }
}
