package querydsl.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import querydsl.dto.ErrorResponse;
import querydsl.query.InvalidFieldException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/test/endpoint");
        webRequest = new ServletWebRequest(request);
    }
    
    @Test
    void testHandleEntityNotFoundException() {
        EntityNotFoundException ex = new EntityNotFoundException("User", 1L);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleEntityNotFound(ex, webRequest);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("User not found with id: 1", response.getBody().getMessage());
    }
    
    @Test
    void testHandleValidationException() {
        ValidationException ex = new ValidationException("Invalid input");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(ex, webRequest);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Invalid input", response.getBody().getMessage());
    }
    
    @Test
    void testHandleQueryException() {
        QueryException ex = new QueryException("Query failed");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleQueryException(ex, webRequest);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Query failed", response.getBody().getMessage());
    }
    
    @Test
    void testHandleInvalidFieldException() {
        InvalidFieldException ex = new InvalidFieldException("invalidField", String.class);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidFieldException(ex, webRequest);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("invalidField"));
    }
    
    @Test
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex, webRequest);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Invalid argument", response.getBody().getMessage());
    }
    
    @Test
    void testHandleGenericException() {
        Exception ex = new Exception("Unexpected error");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, webRequest);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("unexpected error"));
    }
}
