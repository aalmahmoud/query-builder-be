package querydsl.query;

import org.junit.jupiter.api.Test;
import querydsl.model.User;
import querydsl.query.computed.ComputedFieldHandlerRegistry;
import querydsl.service.QueryMetadataService;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryMetadataServiceTest {

    private final QueryMetadataService svc = new QueryMetadataService(new ComputedFieldHandlerRegistry());

    @Test
    void describesUser_withTypesAndOps_andHidesSensitiveFields() {
        EntityMetadata md = svc.describe(User.class);
        assertEquals("user", md.entity());

        Map<String, FieldMeta> byName = md.fields().stream()
                .collect(Collectors.toMap(FieldMeta::name, Function.identity()));

        // Sensitive columns must never be advertised (they're not in @FilterableFields/@SortableFields).
        assertFalse(byName.containsKey("password"), "password must not be queryable");
        assertFalse(byName.containsKey("nationalId"), "nationalId must not be queryable");

        FieldMeta email = byName.get("email");
        assertNotNull(email);
        assertEquals("string", email.type());
        assertTrue(email.filterable());
        assertTrue(email.operations().contains("CONTAINS"));
        assertTrue(email.operations().contains("IS_NULL"));

        assertEquals("boolean", byName.get("isActive").type());
        assertTrue(byName.get("isActive").operations().contains("IS_TRUE"));

        assertEquals("number", byName.get("id").type());
        assertTrue(byName.get("id").operations().contains("GREATER_THAN"));

        assertEquals("datetime", byName.get("createdDate").type());
        assertEquals("string", byName.get("role.name").type());

        // sortable flag reflects @SortableFields (email is sortable; mobileNumber is not)
        assertTrue(byName.get("email").sortable());
        assertFalse(byName.get("mobileNumber").sortable());
    }
}
