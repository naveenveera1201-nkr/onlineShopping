package com.mongodb;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class ProcessQueryBuilder {
    private final List<Bson> filters = new ArrayList<>();
    private Document sort = new Document("createdDate", -1);
    private int skip = 0;
    private int limit = 100;

    public ProcessQueryBuilder byProcessCode(String processCode) {
        filters.add(eq("processCode", processCode));
        return this;
    }

    public ProcessQueryBuilder byStatus(Integer status) {
        filters.add(eq("currentStatus", status));
        return this;
    }

    public ProcessQueryBuilder byCreatedBy(String createdBy) {
        filters.add(eq("createdBy", createdBy));
        return this;
    }

    public ProcessQueryBuilder createdAfter(Date date) {
        filters.add(gte("createdDate", date));
        return this;
    }

    public ProcessQueryBuilder createdBefore(Date date) {
        filters.add(lte("createdDate", date));
        return this;
    }

    public ProcessQueryBuilder sortBy(String field, int direction) {
        this.sort = new Document(field, direction);
        return this;
    }

    public ProcessQueryBuilder skip(int skip) {
        this.skip = skip;
        return this;
    }

    public ProcessQueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Bson build() {
        return filters.isEmpty() ? new Document() : and(filters);
    }

    public Document getSort() {
        return sort;
    }

    public int getSkip() {
        return skip;
    }

    public int getLimit() {
        return limit;
    }
}