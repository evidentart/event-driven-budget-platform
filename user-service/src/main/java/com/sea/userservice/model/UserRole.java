package com.sea.userservice.model;

public enum UserRole {
    USER,
    ADMIN;

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
