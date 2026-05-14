package org.ciberaccion.booklibrary;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.microprofile.auth.LoginConfig;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@LoginConfig(authMethod = "MP-JWT")
@ApplicationPath("/")
public class BookLibrarySecurityApp extends Application {
}