package org.ciberaccion.booklibrary.service;

import org.ciberaccion.booklibrary.dto.AuthDTO;
import org.ciberaccion.booklibrary.entity.Role;
import org.ciberaccion.booklibrary.entity.User;
import org.ciberaccion.booklibrary.security.TokenService;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    @Inject
    TokenService tokenService;

@Transactional
public AuthDTO.LoginResponse register(AuthDTO.RegisterRequest request) {

    if (User.existsByUsername(request.username)) {
        throw new RuntimeException("El usuario ya existe: " + request.username);
    }

    Role role;
    try {
        role = Role.valueOf(request.role.toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new RuntimeException("Rol inválido: " + request.role);
    }

    User user = new User();
    user.username = request.username;
    user.password = BcryptUtil.bcryptHash(request.password);
    user.role = role;
    user.persist();

    AuthDTO.LoginResponse response = new AuthDTO.LoginResponse();
    response.token = tokenService.generateToken(user);
    response.username = user.username;
    response.role = user.role.name();
    return response;
}

    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        User user = User.findByUsername(request.username);

        if (user == null || !BcryptUtil.matches(request.password, user.password)) {
            throw new RuntimeException("Credenciales inválidas");
        }

        AuthDTO.LoginResponse response = new AuthDTO.LoginResponse();
        response.token = tokenService.generateToken(user);
        response.username = user.username;
        response.role = user.role.name();
        return response;
    }
}