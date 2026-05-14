package org.ciberaccion.booklibrary.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.ciberaccion.booklibrary.dto.AuthDTO;
import org.ciberaccion.booklibrary.service.AuthService;

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