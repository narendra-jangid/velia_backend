package com.curasync.user.service;

import com.curasync.user.exception.UserNotFoundException;
import com.curasync.user.model.CartItem;
import com.curasync.user.model.User;
import com.curasync.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CartService {

    private final UserRepository userRepository;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public CartService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<CartItem> getCart(String phone) {
        return new ArrayList<>(ensureCart(getUser(phone)));
    }

    public List<CartItem> addItem(String phone, Map<String, Object> request) {
        User user = getUser(phone);
        List<CartItem> cart = ensureCart(user);

        String productId = str(request, "productId");
        if (productId == null) productId = str(request, "id");
        if (productId == null) {
            throw new IllegalStateException("productId (or id) is required.");
        }

        int qty = toInt(request.get("qty"), 1);
        if (qty < 1) throw new IllegalStateException("Quantity must be at least 1.");

        String size  = str(request, "size");
        String color = str(request, "color");

        for (CartItem existing : cart) {
            if (productId.equals(existing.getProductId())
                    && Objects.equals(size, existing.getSize())
                    && Objects.equals(color, existing.getColor())) {
                existing.setQty(existing.getQty() + qty);
                user.setCart(cart);
                userRepository.save(user);
                log.info("Cart item qty updated for {} product {}", phone, productId);
                return cart;
            }
        }

        cart.add(CartItem.builder()
                .productId(productId)
                .name(str(request, "name"))
                .img(str(request, "img"))
                .price(toDouble(request.get("price")))
                .qty(qty)
                .size(size)
                .color(color)
                .build());

        user.setCart(cart);
        userRepository.save(user);
        log.info("Cart item added for {} product {}", phone, productId);
        return cart;
    }

    public List<CartItem> updateItem(String phone, String productId, Map<String, Object> request) {
        User user = getUser(phone);
        List<CartItem> cart = ensureCart(user);

        String size  = request.containsKey("size")  ? str(request, "size")  : null;
        String color = request.containsKey("color") ? str(request, "color") : null;

        CartItem item = resolveItem(cart, productId, size, color);
        if (item == null) {
            throw new IllegalStateException("Cart item not found for product " + productId);
        }

        if (request.containsKey("qty")) {
            int qty = toInt(request.get("qty"), item.getQty());
            if (qty < 1) throw new IllegalStateException("Quantity must be at least 1.");
            item.setQty(qty);
        }
        if (request.containsKey("size"))  item.setSize(str(request, "size"));
        if (request.containsKey("color")) item.setColor(str(request, "color"));
        if (request.containsKey("name"))  item.setName(str(request, "name"));
        if (request.containsKey("img"))   item.setImg(str(request, "img"));
        if (request.containsKey("price")) item.setPrice(toDouble(request.get("price")));

        user.setCart(cart);
        userRepository.save(user);
        log.info("Cart item updated for {} product {}", phone, productId);
        return cart;
    }

    public List<CartItem> removeItem(String phone, String productId, String size, String color) {
        User user = getUser(phone);
        List<CartItem> cart = ensureCart(user);

        CartItem item = resolveItem(cart, productId, size, color);
        if (item == null) {
            throw new IllegalStateException("Cart item not found for product " + productId);
        }

        cart.remove(item);
        user.setCart(cart);
        userRepository.save(user);
        log.info("Cart item removed for {} product {}", phone, productId);
        return cart;
    }

    public List<CartItem> clearCart(String phone) {
        User user = getUser(phone);
        user.setCart(new ArrayList<>());
        userRepository.save(user);
        log.info("Cart cleared for {}", phone);
        return user.getCart();
    }

    private User getUser(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException(phone));
    }

    private List<CartItem> ensureCart(User user) {
        if (user.getCart() == null) {
            user.setCart(new ArrayList<>());
        }
        return user.getCart();
    }

    private CartItem resolveItem(List<CartItem> cart, String productId, String size, String color) {
        CartItem exact = findItem(cart, productId, size, color);
        if (exact != null) return exact;

        List<CartItem> matches = cart.stream()
                .filter(i -> productId.equals(i.getProductId()))
                .toList();
        if (matches.size() == 1) return matches.get(0);
        return null;
    }

    private CartItem findItem(List<CartItem> cart, String productId, String size, String color) {
        for (CartItem item : cart) {
            if (productId.equals(item.getProductId())
                    && Objects.equals(size, item.getSize())
                    && Objects.equals(color, item.getColor())) {
                return item;
            }
        }
        return null;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private int toInt(Object v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

}
