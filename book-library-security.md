# Book Library Security — Guía de aprendizaje Quarkus JWT + Roles

Documento de referencia construido durante el desarrollo paso a paso del proyecto `book-library-security`. Incluye comparaciones con Spring Security, preguntas, respuestas y observaciones del proceso de aprendizaje.

---

## ¿Cómo funciona JWT en Quarkus?

Quarkus usa **MicroProfile JWT** — un estándar de Jakarta. El flujo es:

1. El cliente manda `POST /auth/login` con usuario y password
2. El servidor valida las credenciales y genera un JWT firmado con la **clave privada**
3. El cliente guarda ese JWT y lo manda en cada request: `Authorization: Bearer <token>`
4. Quarkus valida el token automáticamente con la **clave pública** antes de entrar al endpoint
5. Si el token es válido y el rol es correcto, deja pasar

### Clave privada vs clave pública

Ambas las tiene el **servidor**:
- **Clave privada** — firma el JWT al generarlo. Solo el servidor la conoce, nunca sale.
- **Clave pública** — verifica que el JWT fue firmado con la privada. Quarkus la usa internamente para validar cada request.

El cliente solo recibe y guarda el JWT, no necesita ninguna clave.

### Generar el par de claves RSA

```bash
# Generar clave privada
openssl genrsa -out src/main/resources/privateKey.pem 2048

# Extraer clave pública
openssl rsa -in src/main/resources/privateKey.pem -pubout -out src/main/resources/publicKey.pem
```

---

## Tabla de comparación rápida

| Concepto | Quarkus | Spring Security |
|---|---|---|
| Mecanismo JWT | MicroProfile JWT (`@LoginConfig`) | `spring-security-oauth2-resource-server` |
| Proteger endpoint por rol | `@RolesAllowed("ADMIN")` | `@PreAuthorize("hasRole('ADMIN')")` |
| Permitir acceso sin token | `@PermitAll` | `permitAll()` en SecurityFilterChain |
| Requerir autenticación | `@Authenticated` | `authenticated()` en SecurityFilterChain |
| Hash de password | `BcryptUtil.bcryptHash()` | `BCryptPasswordEncoder` |
| Verificar password | `BcryptUtil.matches()` | `passwordEncoder.matches()` |
| Rutas protegidas | `application.properties` | `SecurityFilterChain` |

---

## Dependencias (`pom.xml`)

```xml
<artifactId>quarkus-rest</artifactId>
<artifactId>quarkus-rest-jackson</artifactId>
<artifactId>quarkus-hibernate-orm-panache</artifactId>
<artifactId>quarkus-jdbc-postgresql</artifactId>
<artifactId>quarkus-hibernate-validator</artifactId>
<artifactId>quarkus-smallrye-openapi</artifactId>
<artifactId>quarkus-smallrye-jwt</artifactId>           <!-- Validación JWT -->
<artifactId>quarkus-smallrye-jwt-build</artifactId>     <!-- Generación JWT -->
<artifactId>quarkus-elytron-security-common</artifactId> <!-- BCryptUtil -->
```

> **Nota:** `quarkus-elytron-security-common` no viene en code.quarkus.io — hay que agregarlo manualmente al `pom.xml` para tener acceso a `BcryptUtil`.

---

## `application.properties`

```properties
# Base de datos
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=booklibrary
quarkus.datasource.password=booklibrary
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5436/booklibrary

# Hibernate — nuevo nombre en Quarkus 3.15+
quarkus.hibernate-orm.schema-management.strategy=drop-and-create
quarkus.hibernate-orm.log.sql=true

# Puerto
quarkus.http.port=8080

# Swagger UI
quarkus.swagger-ui.always-include=true

# JWT
mp.jwt.verify.publickey.location=publicKey.pem
mp.jwt.verify.issuer=book-library-security
smallrye.jwt.sign.key.location=privateKey.pem

# Seguridad — rutas protegidas
quarkus.http.auth.permission.authenticated.paths=/api/*
quarkus.http.auth.permission.authenticated.policy=authenticated

# Rutas públicas — separadas por coma en una sola línea (no duplicar la clave)
quarkus.http.auth.permission.public.paths=/auth/*,/q/*
quarkus.http.auth.permission.public.policy=permit
```

### ¿Qué hace `mp.jwt.verify.issuer`?

Es la "firma de identidad" del token — identifica quién lo emitió. Cuando Quarkus valida un JWT entrante verifica que el campo `iss` dentro del token coincida con este valor. Si alguien presenta un token válido pero generado por otro servidor con un issuer diferente, Quarkus lo rechaza.

### ¿Qué diferencia hay entre `authenticated` y `permit`?

- `authenticated` — solo usuarios con token válido pueden acceder
- `permit` — cualquiera puede acceder, con o sin token

> **Error común:** No duplicar `quarkus.http.auth.permission.public.paths` con dos líneas separadas. Quarkus lanza un warning y solo toma el último valor. Usar una sola línea con comas.

---

## Estructura del proyecto

```
book-library-security/
├── src/main/resources/
│   ├── application.properties
│   ├── privateKey.pem          ← clave privada RSA (no subir a git)
│   └── publicKey.pem           ← clave pública RSA
└── src/main/java/org/ciberaccion/booklibrary/
    ├── BookLibrarySecurityApp.java   ← entry point con @LoginConfig
    ├── entity/
    │   ├── Role.java                 ← enum con los roles
    │   └── User.java                 ← entidad de usuario
    ├── dto/
    │   └── AuthDTO.java              ← Request / LoginResponse
    ├── security/
    │   └── TokenService.java         ← generador de JWT
    ├── service/
    │   └── AuthService.java          ← register / login
    └── resource/
        ├── AuthResource.java         ← endpoints públicos
        └── BookResource.java         ← endpoints protegidos
```

---

## Entidades

### `Role.java` — enum

```java
public enum Role {
    ADMIN,
    EDITOR,
    READER
}
```

**¿Por qué enum y no String?**
Con un `String` nada impide guardar `"ADMINISTRADOR"`, `"admin"` o `"Admon"`. Con el enum solo existen exactamente tres valores válidos y el compilador avisa si escribes uno que no existe.

### `User.java`

```java
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
```

### `@Enumerated(EnumType.STRING)` — ¿qué hace?

Le dice a Hibernate que guarde el enum como texto (`"ADMIN"`, `"EDITOR"`, `"READER"`) en vez de número. Sin esta anotación Hibernate usa `EnumType.ORDINAL` por defecto:

- `ADMIN` → 0
- `EDITOR` → 1
- `READER` → 2

El problema del ordinal: si agregas o reordenas roles en el enum, todos los datos existentes quedarían con el rol incorrecto sin ningún error. Con `STRING` es seguro ante cambios.

Hibernate también genera automáticamente un `CHECK CONSTRAINT` en PostgreSQL:
```sql
role varchar(20) not null check ((role in ('ADMIN','EDITOR','READER')))
```

---

## DTOs

```java
public class AuthDTO {

    public static class RegisterRequest {
        @NotBlank(message = "El username es obligatorio")
        public String username;

        @NotBlank(message = "El password es obligatorio")
        public String password;

        @NotBlank(message = "El rol es obligatorio")
        public String role;  // String, no enum — el cliente manda texto plano
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
```

### ¿Por qué `role` es String en el Request y no el enum `Role`?

El cliente no sabe nada del enum de Java — manda JSON con texto plano como `"ADMIN"`. Recibirlo como `String` es más flexible y permite validar con un mensaje de error claro si mandan un rol inválido.

### ¿Por qué al registrarse devolvemos el token y no solo un mensaje?

Es una decisión de UX — evitar que el usuario tenga que hacer register y luego login por separado. Ambos enfoques son válidos dependiendo del diseño del producto.

---

## BCrypt — passwords seguros

Nunca se guardan passwords en texto plano. BCrypt es un algoritmo de hashing que convierte `"mi_password"` en algo como `"$2a$10$xyz..."` que no se puede revertir. Al hacer login, BCrypt compara el password enviado contra el hash guardado.

```java
// Hashear al registrar
BcryptUtil.bcryptHash("mi_password")

// Verificar al hacer login
BcryptUtil.matches("mi_password", hashGuardado)
```

---

## TokenService — generador de JWT

```java
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
```

### ¿Qué contiene un JWT?

Un JWT tiene tres partes separadas por puntos:

```
eyJhbGci...  .  eyJzdWIi...  .  abc123...
   HEADER    .    PAYLOAD    .   FIRMA
```

- **Header** — algoritmo usado para firmar (`RS256`)
- **Payload** — los datos del token
- **Firma** — garantiza que nadie modificó el token

El payload de nuestro token decodificado en https://jwt.io:

```json
{
  "iss": "book-library-security",
  "sub": "admin",
  "groups": ["ADMIN"],
  "iat": 1778791883,
  "exp": 1778878283,
  "jti": "74e49f33-81f4-4d23-a444-b06f1fe4ae99"
}
```

- `iss` → issuer — quién emitió el token
- `sub` → subject — el username
- `groups` → los roles — así Quarkus sabe los permisos
- `iat` → issued at — cuándo se emitió
- `exp` → expiration — cuándo expira
- `jti` → ID único del token

> **Importante:** El payload es legible por cualquiera — solo está en Base64, no cifrado. Por eso nunca metas passwords ni datos sensibles en el token. La firma es lo que no se puede falsificar.

---

## AuthService

```java
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
```

### ¿Por qué `login` no tiene `@Transactional` pero `register` sí?

`register` escribe en la BD (`persist()`), por eso necesita `@Transactional`. `login` solo lee y genera un token — no cambia el estado de la BD.

---

## Anotaciones de seguridad

### `@LoginConfig` — entry point

```java
@LoginConfig(authMethod = "MP-JWT")
@ApplicationPath("/")
public class BookLibrarySecurityApp extends Application {
}
```

Le dice a Quarkus que use MicroProfile JWT como mecanismo de autenticación. Sin esto los tokens no serían validados.

### `@PermitAll` — acceso sin token

```java
@POST
@Path("/register")
@PermitAll
public Response register(@Valid AuthDTO.RegisterRequest request) { ... }
```

Permite acceso a cualquiera, incluso sin token. Necesario en `/register` y `/login` — si los protegieras nadie podría obtener un token.

### `@RolesAllowed` — autorización por rol

```java
@GET
@RolesAllowed({"ADMIN", "EDITOR", "READER"})  // todos los roles
public String getAll() { ... }

@POST
@RolesAllowed({"ADMIN", "EDITOR"})             // solo ADMIN y EDITOR
public String create() { ... }

@DELETE
@Path("/{id}")
@RolesAllowed("ADMIN")                          // solo ADMIN
public String delete(@PathParam("id") Long id) { ... }
```

**¿A nivel de clase vs a nivel de método?**
- A nivel de clase aplica como default para todos los métodos
- A nivel de método sobreescribe ese default
- En nuestro caso va por método porque cada endpoint tiene reglas diferentes

### `@Authenticated` — requerir token sin importar el rol

```java
@Authenticated
public class BookResource { ... }
```

Requiere que el request tenga un token válido, sin importar el rol. Si solo usas `@RolesAllowed` en los métodos pero no hay `@Authenticated` en la clase, un request sin token podría colarse.

---

## Comportamiento de los status codes

| Situación | Status |
|---|---|
| Token válido + rol correcto | 200 OK |
| Sin token | 401 Unauthorized |
| Token válido + rol incorrecto | 403 Forbidden |

### 401 vs 403 — diferencia importante

- **401 Unauthorized** — no mandaste token o el token es inválido
- **403 Forbidden** — el token es válido pero tu rol no tiene permiso para ese endpoint

---

## AuthResource — endpoints públicos

```java
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/register")
    @PermitAll
    public Response register(@Valid AuthDTO.RegisterRequest request) {
        AuthDTO.LoginResponse response = authService.register(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public AuthDTO.LoginResponse login(@Valid AuthDTO.LoginRequest request) {
        return authService.login(request);
    }
}
```

## BookResource — endpoints protegidos

```java
@Path("/api/v1/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BookResource {

    @GET
    @RolesAllowed({"ADMIN", "EDITOR", "READER"})
    public String getAll() {
        return "{\"message\": \"Lista de libros — acceso para todos los roles\"}";
    }

    @POST
    @RolesAllowed({"ADMIN", "EDITOR"})
    public String create() {
        return "{\"message\": \"Libro creado — solo ADMIN y EDITOR\"}";
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    public String delete(@PathParam("id") Long id) {
        return "{\"message\": \"Libro eliminado — solo ADMIN\"}";
    }
}
```

---

## Flujo completo probado

```
POST /auth/register  { username, password, role }
  → 201 Created + { token, username, role }

POST /auth/login  { username, password }
  → 200 OK + { token, username, role }

GET /api/v1/books  (sin token)
  → 401 Unauthorized

GET /api/v1/books  (token READER)
  → 200 OK

POST /api/v1/books  (token READER)
  → 403 Forbidden

DELETE /api/v1/books/1  (token READER)
  → 403 Forbidden

GET /api/v1/books  (token ADMIN)
  → 200 OK

POST /api/v1/books  (token ADMIN)
  → 200 OK

DELETE /api/v1/books/1  (token ADMIN)
  → 200 OK
```

---

## Cómo correr el proyecto

```bash
# 1. Levantar PostgreSQL (puerto 5436 para no chocar con book-library)
docker run --name booklibrary-security \
  -e POSTGRES_DB=booklibrary \
  -e POSTGRES_USER=booklibrary \
  -e POSTGRES_PASSWORD=booklibrary \
  -p 5436:5432 -d postgres:16

# 2. Dev Mode
./mvnw quarkus:dev

# 3. Swagger UI
# http://localhost:8080/q/swagger-ui
# Usar el botón Authorize 🔒 con: Bearer <token>
```

---

## Dev Services

Quarkus tiene **Dev Services** — si no configuras una URL de BD en `application.properties`, Quarkus levanta automáticamente un contenedor PostgreSQL con Testcontainers. En este proyecto lo configuramos manual para entender qué ocurre por debajo.

---

## Notas importantes

- Las claves `privateKey.pem` y `publicKey.pem` **nunca deben subirse a git**. Agrégalas al `.gitignore`.
- En producción las claves se inyectan como variables de entorno, no como archivos en el proyecto.
- El payload del JWT es legible — nunca incluir passwords ni datos sensibles.
- `quarkus.hibernate-orm.database.generation` está deprecated en Quarkus 3.15+. Usar `quarkus.hibernate-orm.schema-management.strategy`.
