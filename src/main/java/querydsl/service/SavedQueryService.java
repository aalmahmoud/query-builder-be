package querydsl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import querydsl.dto.SavedQueryDto;
import querydsl.exception.EntityNotFoundException;
import querydsl.exception.QueryException;
import querydsl.model.SavedQuery;
import querydsl.query.QueryRequest;
import querydsl.repository.SavedQueryRepository;

import java.util.List;
import java.util.Set;

/**
 * CRUD for saved queries, scoped per entity and per owner (the authenticated principal).
 * A user only ever sees and deletes their own saved queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedQueryService {

    private static final Set<String> ALLOWED_ENTITIES = Set.of("user", "role", "permission");

    private final SavedQueryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SavedQueryDto.Response create(String entity, SavedQueryDto.Request request) {
        validateEntity(entity);
        SavedQuery saved = new SavedQuery();
        saved.setEntityName(entity);
        saved.setName(request.getName());
        saved.setQueryJson(serialize(request.getQueryRequest()));
        repository.save(saved);
        log.info("Saved query '{}' for {} by {}", saved.getName(), entity, saved.getCreatedBy());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedQueryDto.Response> list(String entity) {
        validateEntity(entity);
        return repository
                .findByEntityNameAndCreatedByOrderByCreatedDateDesc(entity, currentUser())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(String entity, Long id) {
        validateEntity(entity);
        SavedQuery sq = repository.findByIdAndEntityNameAndCreatedBy(id, entity, currentUser())
                .orElseThrow(() -> new EntityNotFoundException("SavedQuery", id));
        repository.delete(sq);
    }

    private void validateEntity(String entity) {
        if (!ALLOWED_ENTITIES.contains(entity)) {
            throw new QueryException("Unknown entity '" + entity + "'");
        }
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() ? a.getName() : "system";
    }

    private String serialize(QueryRequest qr) {
        try {
            return objectMapper.writeValueAsString(qr);
        } catch (JsonProcessingException e) {
            throw new QueryException("Could not serialize query: " + e.getOriginalMessage());
        }
    }

    private QueryRequest deserialize(String json) {
        try {
            return objectMapper.readValue(json, QueryRequest.class);
        } catch (JsonProcessingException e) {
            throw new QueryException("Stored query is corrupt: " + e.getOriginalMessage());
        }
    }

    private SavedQueryDto.Response toResponse(SavedQuery sq) {
        SavedQueryDto.Response r = new SavedQueryDto.Response();
        r.setId(sq.getId());
        r.setEntity(sq.getEntityName());
        r.setName(sq.getName());
        r.setQueryRequest(deserialize(sq.getQueryJson()));
        r.setCreatedBy(sq.getCreatedBy());
        r.setCreatedDate(sq.getCreatedDate());
        return r;
    }
}
