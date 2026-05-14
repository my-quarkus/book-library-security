package org.ciberaccion.booklibrary.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 100)
    public String username;

    @NotBlank
    @Column(nullable = false)
    public String password;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role;

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }

    public static boolean existsByUsername(String username) {
        return count("username", username) > 0;
    }
}
