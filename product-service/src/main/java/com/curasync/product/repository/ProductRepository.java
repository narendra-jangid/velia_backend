package com.curasync.product.repository;

import com.curasync.product.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository — same operations as the Velia
 * Mongoose calls (find, findById, create, findByIdAndUpdate, findByIdAndDelete)
 * but expressed as a Java interface; no implementation needed.
 */
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    // Equivalent to Mongoose: Product.find({ category })
    List<Product> findByCategory(String category);

    // Equivalent to Mongoose: Product.find({ featured: true })
    List<Product> findByFeaturedTrue();

    // Equivalent to Mongoose: Product.find({ active: true })
    List<Product> findByActiveTrue();

    // Equivalent to Mongoose: Product.find({ slug })
    Optional<Product> findBySlug(String slug);
}
