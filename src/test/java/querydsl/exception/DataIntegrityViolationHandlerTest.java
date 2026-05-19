package querydsl.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import querydsl.dto.ErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Phase 3 fix 3.8 (and the AccessDeniedException handler added with it).
 *
 * <p>Previously these exceptions fell into the generic 500 branch — clients got an opaque
 * "An unexpected error occurred" payload. Now they map to proper 409 / 403 with parseable
 * constraint-aware messages.
 */
class DataIntegrityViolationHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/user");
        webRequest = new ServletWebRequest(req);
    }

    @Test
    void duplicateEmailConstraint_yields409WithFriendlyMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"users_email_key\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, webRequest);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().toLowerCase().contains("email"));
    }

    @Test
    void duplicateRoleNameConstraint_yields409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"roles_name_key\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, webRequest);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().getMessage().toLowerCase().contains("role"));
    }

    @Test
    void fkUserRoleViolation_yields409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: update or delete on table \"roles\" violates foreign key constraint \"fk_user_role\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, webRequest);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().getMessage().toLowerCase().contains("users still reference"));
    }

    @Test
    void unmappedConstraint_yieldsGeneric409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: something weird happened"));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, webRequest);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Data integrity constraint violated.", response.getBody().getMessage());
    }

    @Test
    void accessDenied_yields403() {
        AccessDeniedException ex = new AccessDeniedException("forbidden");
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, webRequest);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
    }
}
