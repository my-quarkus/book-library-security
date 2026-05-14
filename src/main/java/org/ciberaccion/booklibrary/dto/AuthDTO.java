package org.ciberaccion.booklibrary.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDTO {

    public static class RegisterRequest {

        @NotBlank(message = "El username es obligatorio")
        public String username;

        @NotBlank(message = "El password es obligatorio")
        public String password;

        @NotBlank(message = "El rol es obligatorio")
        public String role;
    }

    public static class LoginRequest {

        @NotBlank(message = "El username es obligatorio")
        public String username;

        @NotBlank(message = "El password es obligatorio")
        public String password;
    }

    public static class LoginResponse {
        public String token;
        public String username;
        public String role;
    }
}