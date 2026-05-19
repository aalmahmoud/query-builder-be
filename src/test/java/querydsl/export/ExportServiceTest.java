package querydsl.export;

import org.junit.jupiter.api.Test;
import querydsl.exception.QueryException;
import querydsl.model.Permission;
import querydsl.model.Role;
import querydsl.model.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for ExportService.
 *
 * <p>Phase 1 fix 1.1: the previous implementation called {@code setAccessible(true)} on
 * private fields, which leaked {@code User.password} (BCrypt hash) to any authenticated
 * caller via {@code POST /user/export/query} with {@code selectedColumns: ["password"]}.
 * Fix: every entity must declare an {@link Exportable} allow-list; requested columns
 * outside that list are rejected; private fields are no longer reachable.
 */
class ExportServiceTest {

    private final ExportService service = new ExportService();

    @Test
    void requestingPassword_isRejected() {
        User u = newUser();
        u.setPassword("$2a$10$leakedhash");

        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("email", "password"));

        QueryException ex = assertThrows(QueryException.class,
                () -> service.exportData(List.of(u), req));
        assertTrue(ex.getMessage().contains("password"),
                "Error must name the rejected column. Got: " + ex.getMessage());
    }

    @Test
    void requestingAllowedColumns_works() {
        User u = newUser();
        u.setEmail("jane@example.com");
        u.setFirstName("Jane");

        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("email", "firstName"));

        byte[] bytes = service.exportData(List.of(u), req);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Excel byte stream should be non-empty");
    }

    @Test
    void requestingNestedAllowedPath_works() {
        Role role = new Role();
        role.setName("ADMIN");
        User u = newUser();
        u.setRole(role);

        ExportRequest req = new ExportRequest();
        req.setFormat("PDF");
        req.setSelectedColumns(List.of("email", "role.name"));

        byte[] bytes = service.exportData(List.of(u), req);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void requestingUnknownColumn_isRejected() {
        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("totallyMadeUpField"));

        assertThrows(QueryException.class,
                () -> service.exportData(List.of(newUser()), req));
    }

    @Test
    void entityWithoutExportableAnnotation_isRejected() {
        // Anonymous local class - not annotated with @Exportable.
        class NotExportable {
            public String getSecret() { return "shh"; }
        }
        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("secret"));

        QueryException ex = assertThrows(QueryException.class,
                () -> service.exportData(List.of(new NotExportable()), req));
        assertTrue(ex.getMessage().contains("@Exportable"));
    }

    @Test
    void emptyEntityList_returnsEmptyBytes() {
        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("email"));
        assertEquals(0, service.exportData(List.<User>of(), req).length);
    }

    @Test
    void nullSelectedColumns_returnsEmptyBytes() {
        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(null);
        assertEquals(0, service.exportData(List.of(newUser()), req).length);
    }

    @Test
    void permissionExport_worksOnAllowedFields() {
        Permission p = new Permission();
        p.setName("user:create");
        p.setResource("user");
        p.setAction("create");

        ExportRequest req = new ExportRequest();
        req.setFormat("EXCEL");
        req.setSelectedColumns(List.of("name", "resource", "action"));

        byte[] bytes = service.exportData(List.of(p), req);
        assertTrue(bytes.length > 0);
    }

    private static User newUser() {
        User u = new User();
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail("test@example.com");
        u.setNationalId("999");
        u.setIsActive(true);
        return u;
    }
}
