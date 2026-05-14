package org.ciberaccion.booklibrary.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
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