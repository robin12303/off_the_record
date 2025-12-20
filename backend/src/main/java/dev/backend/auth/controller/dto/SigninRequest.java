package dev.backend.auth.controller.dto;

public record SigninRequest(    String name,
                                String email,
                                String password) {

}
