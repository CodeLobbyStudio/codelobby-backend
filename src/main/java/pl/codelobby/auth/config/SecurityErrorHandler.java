package pl.codelobby.auth.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pl.codelobby.common.error.ApiErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    private final BearerTokenAccessDeniedHandler bearerDeniedHandler =
            new BearerTokenAccessDeniedHandler();


    public SecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        bearerEntryPoint.commence(request, response, exception);

        int status = response.getStatus();

        if (status == HttpServletResponse.SC_BAD_REQUEST) {
            writeError(
                    response,
                    "INVALID_REQUEST",
                    "Invalid authentication request"
            );
            return;
        }

        writeError(
                response,
                "UNAUTHORIZED",
                "Authentication is required"
        );
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception
    ) throws IOException, ServletException {

        bearerDeniedHandler.handle(request, response, exception);

        writeError(
                response,
                "FORBIDDEN",
                "Access denied"
        );
    }

    private void writeError(
            HttpServletResponse response,
            String code,
            String message
    ) throws IOException {

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse error = new ApiErrorResponse(
                code,
                message,
                Instant.now()
        );

        jsonMapper.writeValue(response.getOutputStream(), error);
    }
}


