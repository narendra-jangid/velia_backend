package com.curasync.user.service;

import com.curasync.user.exception.UserNotFoundException;
import com.curasync.user.model.User;
import com.curasync.user.repository.UserRepository;
import com.curasync.user.util.PincodeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates the user record on first login, or returns the existing one
     * (optionally refreshing the name) on repeat logins.
     */
    public User verifyAndUpsert(String phone, String name) {
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> User.builder().phone(phone).build());

        if (name != null && !name.isBlank()) {
            user.setName(name);
        }

        User saved = userRepository.save(user);
        log.info("User upserted for phone {}", phone);
        return saved;
    }

    public User getByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new UserNotFoundException(phone));
    }

    /**
     * Partial update — only fields present in the request map are changed.
     * Accepts pincode, zip, or zipcode for the postal code field.
     */
    public User updateProfile(String phone, Map<String, Object> request) {
        User user = getByPhone(phone);

        if (request.containsKey("name"))    user.setName(str(request, "name"));
        if (request.containsKey("email"))   user.setEmail(str(request, "email"));
        if (request.containsKey("address")) user.setAddress(str(request, "address"));
        if (request.containsKey("city"))    user.setCity(str(request, "city"));
        if (request.containsKey("state"))   user.setState(str(request, "state"));

        String pincode = resolvePincode(request);
        if (pincode != null) {
            if (!PincodeValidator.isValid(pincode)) {
                throw new IllegalStateException("Valid 6-digit pincode required.");
            }
            user.setPincode(pincode);
        }

        User saved = userRepository.save(user);
        log.info("Profile updated for phone {}", phone);
        return saved;
    }

    /** Sync shipping details from a successful checkout.
     *  NOTE: not currently called from anywhere in this codebase — flagged
     *  as unused in the API audit rather than removed. */
    public User syncFromCheckout(String phone, Map<String, Object> customer) {
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> User.builder().phone(phone).build());

        if (customer.containsKey("name"))    user.setName(str(customer, "name"));
        if (customer.containsKey("email"))   user.setEmail(str(customer, "email"));
        if (customer.containsKey("address")) user.setAddress(str(customer, "address"));
        if (customer.containsKey("city"))    user.setCity(str(customer, "city"));
        if (customer.containsKey("state"))   user.setState(str(customer, "state"));

        String pincode = resolvePincode(customer);
        if (pincode != null && PincodeValidator.isValid(pincode)) {
            user.setPincode(pincode);
        }

        return userRepository.save(user);
    }

    private String resolvePincode(Map<String, Object> map) {
        if (map.containsKey("pincode")) return str(map, "pincode");
        if (map.containsKey("zip"))     return str(map, "zip");
        if (map.containsKey("zipcode")) return str(map, "zipcode");
        return null;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString().trim();
    }
}
