package querydsl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom exception classes.
 */
class ExceptionTest {
    
    @Test
    void testEntityNotFoundExceptionWithMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("User not found");
        assertEquals("User not found", ex.getMessage());
    }
    
    @Test
    void testEntityNotFoundExceptionWithEntityAndId() {
        EntityNotFoundException ex = new EntityNotFoundException("User", 1L);
        assertEquals("User not found with id: 1", ex.getMessage());
    }
    
    @Test
    void testEntityNotFoundExceptionWithEntityFieldAndValue() {
        EntityNotFoundException ex = new EntityNotFoundException("User", "email", "test@example.com");
        assertEquals("User not found with email: test@example.com", ex.getMessage());
    }
    
    @Test
    void testValidationExceptionWithMessage() {
        ValidationException ex = new ValidationException("Validation failed");
        assertEquals("Validation failed", ex.getMessage());
    }
    
    @Test
    void testValidationExceptionWithMessageAndCause() {
        Throwable cause = new IllegalArgumentException("Invalid input");
        ValidationException ex = new ValidationException("Validation failed", cause);
        assertEquals("Validation failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
    
    @Test
    void testQueryExceptionWithMessage() {
        QueryException ex = new QueryException("Query failed");
        assertEquals("Query failed", ex.getMessage());
    }
    
    @Test
    void testQueryExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("Database error");
        QueryException ex = new QueryException("Query failed", cause);
        assertEquals("Query failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
