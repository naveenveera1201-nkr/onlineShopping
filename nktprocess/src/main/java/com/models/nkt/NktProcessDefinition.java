package com.models.nkt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Represents a single NKT process entry as declared in the
 * {@code NktProcesses} section of {@code process-flow.json}.
 *
 * <pre>
 * {
 *   "Code":           "nkt.order.place",
 *   "Collection":     "orders",
 *   "Operation":      "CUSTOM",
 *   "HandlerKey":     "ORDER_PLACE",
 *   "Protected":      true,
 *   "RequiredFields": ["storeId", "items"],
 *   "UserIdField":    "customerId"
 * }
 * </pre>
 *
 * Supported {@code Operation} values:
 * <ul>
 *   <li>{@code FIND_ALL}          – findAll(criteria)</li>
 *   <li>{@code FIND_BY_ID}        – findById(data[IdField])</li>
 *   <li>{@code FIND_BY_USER_ID}   – findOne(collection, userId)</li>
 *   <li>{@code INSERT}            – insert(data) with optional userId injection</li>
 *   <li>{@code UPDATE_BY_ID}      – updateById(data[IdField], data fields)</li>
 *   <li>{@code UPDATE_BY_USER_ID} – updateFirst(userId, data fields)</li>
 *   <li>{@code SOFT_DELETE}       – set status=DELETED by id</li>
 *   <li>{@code CUSTOM}            – delegate to a registered handler</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NktProcessDefinition {

	/** Process code — matches {@code @RequestParam("code")}. */
	@JsonProperty("Code")
	private String code;

	/** MongoDB collection name (model-less). */
	@JsonProperty("Collection")
	private String collection;

	/**
	 * Operation type — determines how NktCoreService processes the request. One of:
	 * FIND_ALL, FIND_BY_ID, FIND_BY_USER_ID, INSERT, UPDATE_BY_ID,
	 * UPDATE_BY_USER_ID, SOFT_DELETE, CUSTOM.
	 */
	@JsonProperty("Operation")
	private String operation;

	/**
	 * Key of the registered {@link com.service.handlers.NktOperationHandler} to
	 * invoke when {@code operation} is {@code CUSTOM}.
	 */
	@JsonProperty("HandlerKey")
	private String handlerKey;

	/** When true, a valid JWT must be present in {@code data["token"]}. */
	@JsonProperty("Protected")
	private boolean protectedEndpoint;

	/** Fields that must be present and non-null in the request data. */
	@JsonProperty("RequiredFields")
	private List<String> requiredFields;

	/**
	 * Field in the request data that holds the document id for FIND_BY_ID,
	 * UPDATE_BY_ID, SOFT_DELETE operations. Defaults to {@code "id"} if absent.
	 */
	@JsonProperty("IdField")
	private String idField;

	/**
	 * Static filter criteria merged into every FIND_ALL query. Example:
	 * {@code {"status": "ACTIVE", "active": true}}.
	 */
	@JsonProperty("StaticCriteria")
	private Map<String, Object> staticCriteria;

	/**
	 * Names of fields to copy from request data into the query criteria when
	 * running FIND_ALL queries.
	 */
	@JsonProperty("CriteriaFields")
	private List<String> criteriaFields;

	/**
	 * Field name to set to the extracted {@code userId} when inserting a document
	 * (e.g. {@code "customerId"}, {@code "ownerId"}).
	 */
	@JsonProperty("UserIdField")
	private String userIdField;

	/**
	 * Field to sort by for FIND_ALL queries. Defaults to {@code "createdAt"}.
	 */
	@JsonProperty("SortField")
	private String sortField;

	/** {@code ASC} or {@code DESC} (default {@code DESC}). */
	@JsonProperty("SortDirection")
	private String sortDirection;

	// ── Convenience helpers ───────────────────────────────────────────────────

	public String effectiveIdField() {
		return idField != null ? idField : "id";
	}

	public String effectiveSortField() {
		return sortField != null ? sortField : "createdAt";
	}

	public String effectiveSortDir() {
		return sortDirection != null ? sortDirection : "DESC";
	}
}
