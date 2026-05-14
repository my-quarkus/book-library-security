package org.ciberaccion.booklibrary.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.ciberaccion.booklibrary.entity.User;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    public String generateToken(User user) {
        return Jwt.issuer("book-library-security")
                .subject(user.username)
                .groups(Set.of(user.role.name()))
                .expiresIn(Duration.ofHours(24))
                .sign();
    }
}