package com.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mongodb.bulk.BulkWriteResult;
import com.repository.NktDynamicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin transactional boundary around the three bulk upserts a stock-master
 * import performs (categories, sub_categories, stocks).
 *
 * Kept as its own Spring bean — not a method on {@link ExcelStockImportService}
 * — because {@code @Transactional} only takes effect on a call that goes
 * through the Spring proxy; a self-invoked method on the same bean would
 * silently run non-transactionally. Calling into this separate bean from
 * {@link ExcelStockImportService} keeps the guarantee real: if any of the
 * three bulk writes fails, the whole import rolls back, so a partially
 * written Excel import can never happen (Atlas's cluster is a replica set,
 * so multi-document transactions are available — see {@code AppConfig}'s
 * {@code MongoTransactionManager} bean).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MongoBulkUpsertService {

    private final NktDynamicRepository repo;

    /**
     * @return per-collection {@link BulkWriteResult}s (empty collections are skipped
     *         and simply absent from the map — nothing to upsert, nothing to report).
     */
    @Transactional("mongoTransactionManager")
    public Map<String, BulkWriteResult> upsertImport(List<Map<String, Object>> categoryDocs,
                                                       List<Map<String, Object>> subCategoryDocs,
                                                       List<Map<String, Object>> stockDocs) {

        Map<String, BulkWriteResult> results = new java.util.LinkedHashMap<>();

        if (!categoryDocs.isEmpty()) {
            results.put("categories",
                    repo.bulkUpsertByField("categories", "categoryId", categoryDocs, java.util.Set.of("createdAt")));
        }
        if (!subCategoryDocs.isEmpty()) {
            results.put("sub_categories",
                    repo.bulkUpsertByField("sub_categories", "subCategoryId", subCategoryDocs, java.util.Set.of("createdAt")));
        }
        if (!stockDocs.isEmpty()) {
            results.put("stocks",
                    repo.bulkUpsertByField("stocks", "stockId", stockDocs, java.util.Set.of("createdAt")));
        }

        log.info("MongoBulkUpsertService.upsertImport: categories={}, subCategories={}, stocks={}",
                categoryDocs.size(), subCategoryDocs.size(), stockDocs.size());

        return results;
    }
}
