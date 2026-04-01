package com.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Completely model-less MongoDB repository for the NKT no-code platform.
 *
 * Every method accepts a plain {@code collectionName} string at runtime so
 * no Java entity class is required.  All documents are represented as
 * {@code Map<String, Object>} — they are stored / retrieved as raw BSON
 * Documents and converted automatically.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class NktDynamicRepository {

    private final MongoTemplate mongo;

    // ─── INSERT / SAVE ───────────────────────────────────────────────────────

    /** Insert a new document; auto-generates {@code _id} if absent. */
    public Map<String, Object> insert(String collection, Map<String, Object> data) {
        Document doc = new Document(sanitise(data));
        mongo.insert(doc, collection);
        return toMap(doc);
    }

    /** Save (insert or replace) a document. */
    public Map<String, Object> save(String collection, Map<String, Object> data) {
        Document doc = new Document(sanitise(data));
        mongo.save(doc, collection);
        return toMap(doc);
    }

    // ─── FIND ────────────────────────────────────────────────────────────────

    /** Find a document by its {@code _id} string. */
    public Optional<Map<String, Object>> findById(String collection, String id) {
        Query q = Query.query(Criteria.where("_id").is(toId(id)));
        Document doc = mongo.findOne(q, Document.class, collection);
        return Optional.ofNullable(toMap(doc));
    }

    /** Find the first document matching a single field/value pair. */
    public Optional<Map<String, Object>> findOne(String collection,
                                                  String field, Object value) {
        Query q = Query.query(Criteria.where(field).is(value));
        Document doc = mongo.findOne(q, Document.class, collection);
        return Optional.ofNullable(toMap(doc));
    }

    /** Find the first document matching multiple field/value criteria (AND). */
    public Optional<Map<String, Object>> findOneByCriteria(String collection,
                                                            Map<String, Object> criteria) {
        Document doc = mongo.findOne(buildQuery(criteria), Document.class, collection);
        return Optional.ofNullable(toMap(doc));
    }

    /** Find all documents matching the criteria map. */
    public List<Map<String, Object>> findAll(String collection,
                                              Map<String, Object> criteria) {
        return mongo.find(buildQuery(criteria), Document.class, collection)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    /** Find all documents in the collection (no filter). */
    public List<Map<String, Object>> findAll(String collection) {
        return mongo.findAll(Document.class, collection)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    /** Find with sort, skip, and limit. */
    public List<Map<String, Object>> findAllSorted(String collection,
                                                    Map<String, Object> criteria,
                                                    String sortField,
                                                    Sort.Direction direction,
                                                    int skip, int limit) {
        Query q = buildQuery(criteria)
                .with(Sort.by(direction, sortField))
                .skip(skip).limit(limit);
        return mongo.find(q, Document.class, collection)
                .stream().map(this::toMap).collect(Collectors.toList());
    }

    /** Check existence. */
    public boolean exists(String collection, Map<String, Object> criteria) {
        return mongo.exists(buildQuery(criteria), Document.class, collection);
    }

    public long count(String collection, Map<String, Object> criteria) {
        return mongo.count(buildQuery(criteria), Document.class, collection);
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    /** Apply a partial field update on the document with the given {@code _id}. */
    public void updateById(String collection, String id,
                           Map<String, Object> fields) {
        Query q = Query.query(Criteria.where("_id").is(toId(id)));
        mongo.updateFirst(q, buildUpdate(fields), Document.class, collection);
    }

    /** Apply a partial update on the first document matching criteria. */
    public void updateFirst(String collection, Map<String, Object> criteria,
                            Map<String, Object> fields) {
        mongo.updateFirst(buildQuery(criteria), buildUpdate(fields),
                Document.class, collection);
    }

    /** Apply a partial update on ALL documents matching criteria. */
    public void updateMany(String collection, Map<String, Object> criteria,
                           Map<String, Object> fields) {
        mongo.updateMulti(buildQuery(criteria), buildUpdate(fields),
                Document.class, collection);
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    /** Hard-delete by {@code _id}. */
    public void deleteById(String collection, String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(toId(id))),
                Document.class, collection);
    }

    /** Hard-delete all documents matching criteria. */
    public void deleteAll(String collection, Map<String, Object> criteria) {
        mongo.remove(buildQuery(criteria), Document.class, collection);
    }

    // ─── RAW ACCESS ──────────────────────────────────────────────────────────

    public MongoTemplate template() { return mongo; }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Query buildQuery(Map<String, Object> criteria) {
        if (criteria == null || criteria.isEmpty()) return new Query();
        Criteria c = new Criteria();
        List<Criteria> parts = new ArrayList<>();
        criteria.forEach((field, value) -> parts.add(Criteria.where(field).is(value)));
        return Query.query(c.andOperator(parts.toArray(new Criteria[0])));
    }

    private Update buildUpdate(Map<String, Object> fields) {
        Update u = new Update();
        fields.forEach(u::set);
        return u;
    }

    /** Strip reserved keys the caller should not overwrite in Mongo. */
    private Map<String, Object> sanitise(Map<String, Object> data) {
        Map<String, Object> clean = new LinkedHashMap<>(data);
        clean.remove("token"); // never persist the JWT
        return clean;
    }

    /** Convert a BSON Document to Map, normalising ObjectId → String. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Document doc) {
        if (doc == null) return null;
        Map<String, Object> map = new LinkedHashMap<>(doc);
        Object id = map.remove("_id");
        if (id != null) map.put("id", id instanceof ObjectId ? id.toString() : id);
        return map;
    }

    /**
     * Accept either a 24-char hex ObjectId string or a plain string id.
     * Mongo stores _id as ObjectId when possible, so we try to parse first.
     */
    private Object toId(String id) {
        if (id != null && id.length() == 24) {
            try { return new ObjectId(id); } catch (IllegalArgumentException ignored) {}
        }
        return id;
    }
}
