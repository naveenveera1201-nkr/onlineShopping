package com.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic MongoDB repository for the NKT no-code platform.
 *
 * All insert, update, find and delete operations across every collection
 * are routed through this single class.  Callers pass the collection/entity
 * class at runtime so no per-collection Spring Data repository is needed.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class NktMongoRepository {

    private final MongoTemplate mongoTemplate;

    // ── INSERT / SAVE ─────────────────────────────────────────────────────────

    /**
     * Persist a new document.  Use for fresh inserts.
     */
    public <T> T insert(T entity) {
        log.debug("NktMongoRepository.insert: {}", entity.getClass().getSimpleName());
        return mongoTemplate.insert(entity);
    }

    /**
     * Save (insert or replace) a document.
     */
    public <T> T save(T entity) {
        log.debug("NktMongoRepository.save: {}", entity.getClass().getSimpleName());
        return mongoTemplate.save(entity);
    }

    // ── FIND ──────────────────────────────────────────────────────────────────

    /**
     * Find a document by its {@code _id}.
     */
    public <T> Optional<T> findById(String id, Class<T> entityClass) {
        T result = mongoTemplate.findById(id, entityClass);
        return Optional.ofNullable(result);
    }

    /**
     * Find the first document matching a single field/value pair.
     */
    public <T> Optional<T> findOne(String field, Object value, Class<T> entityClass) {
        Query q = Query.query(Criteria.where(field).is(value));
        return Optional.ofNullable(mongoTemplate.findOne(q, entityClass));
    }

    /**
     * Find all documents matching a single field/value pair.
     */
    public <T> List<T> findAll(String field, Object value, Class<T> entityClass) {
        Query q = Query.query(Criteria.where(field).is(value));
        return mongoTemplate.find(q, entityClass);
    }

    /**
     * Find all documents matching multiple field/value criteria (AND).
     *
     * @param criteria Map of field name → expected value
     */
    public <T> List<T> findAllByCriteria(Map<String, Object> criteria, Class<T> entityClass) {
        Criteria c = new Criteria();
        criteria.forEach((field, value) -> c.and(field).is(value));
        return mongoTemplate.find(Query.query(c), entityClass);
    }

    /**
     * Find all documents in the collection (no filter).
     */
    public <T> List<T> findAll(Class<T> entityClass) {
        return mongoTemplate.findAll(entityClass);
    }

    /**
     * Find documents with a filter map, sorted by a field.
     */
    public <T> List<T> findAllSorted(Map<String, Object> criteria, String sortField,
                                     Sort.Direction direction, int skip, int limit,
                                     Class<T> entityClass) {
        Criteria c = new Criteria();
        criteria.forEach((field, value) -> c.and(field).is(value));
        Query q = Query.query(c)
                .with(Sort.by(direction, sortField))
                .skip(skip)
                .limit(limit);
        return mongoTemplate.find(q, entityClass);
    }

    /**
     * Check whether at least one document matches the given field/value pair.
     */
    public <T> boolean exists(String field, Object value, Class<T> entityClass) {
        Query q = Query.query(Criteria.where(field).is(value));
        return mongoTemplate.exists(q, entityClass);
    }

    /**
     * Check whether at least one document matches multiple criteria (AND).
     */
    public <T> boolean existsByCriteria(Map<String, Object> criteria, Class<T> entityClass) {
        Criteria c = new Criteria();
        criteria.forEach((field, value) -> c.and(field).is(value));
        return mongoTemplate.exists(Query.query(c), entityClass);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Apply a partial field update on the document with the given {@code id}.
     *
     * @param id          document _id
     * @param fields      map of field names → new values
     * @param entityClass target collection
     */
    public <T> void updateById(String id, Map<String, Object> fields, Class<T> entityClass) {
        Query q = Query.query(Criteria.where("_id").is(id));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(q, update, entityClass);
    }

    /**
     * Apply a partial field update on documents matching a single field/value filter.
     */
    public <T> void updateFirst(String filterField, Object filterValue,
                                Map<String, Object> fields, Class<T> entityClass) {
        Query q = Query.query(Criteria.where(filterField).is(filterValue));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(q, update, entityClass);
    }

    /**
     * Apply a partial field update on ALL documents matching the criteria.
     */
    public <T> void updateMany(Map<String, Object> filterCriteria,
                               Map<String, Object> fields, Class<T> entityClass) {
        Criteria c = new Criteria();
        filterCriteria.forEach((field, value) -> c.and(field).is(value));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateMulti(Query.query(c), update, entityClass);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-delete the document with the given id.
     */
    public <T> void deleteById(String id, Class<T> entityClass) {
        Query q = Query.query(Criteria.where("_id").is(id));
        mongoTemplate.remove(q, entityClass);
    }

    /**
     * Hard-delete all documents matching a single field/value filter.
     */
    public <T> void deleteAll(String field, Object value, Class<T> entityClass) {
        Query q = Query.query(Criteria.where(field).is(value));
        mongoTemplate.remove(q, entityClass);
    }

    // ── RAW / UTILITY ─────────────────────────────────────────────────────────

    /**
     * Count documents matching the criteria.
     */
    public <T> long count(Map<String, Object> criteria, Class<T> entityClass) {
        Criteria c = new Criteria();
        criteria.forEach((field, value) -> c.and(field).is(value));
        return mongoTemplate.count(Query.query(c), entityClass);
    }

    /**
     * Expose the underlying {@link MongoTemplate} for complex queries that
     * cannot be expressed through the helper methods above.
     */
    public MongoTemplate template() {
        return mongoTemplate;
    }
}
