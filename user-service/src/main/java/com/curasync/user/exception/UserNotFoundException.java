package com.curasync.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String phone) {
        super("User not found: " + phone);
    }
}
