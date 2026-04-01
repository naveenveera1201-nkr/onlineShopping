package com.constant;

/**
 * Process-code constants for the NammaKadaiTheru (NKT) no-code platform.
 *
 * Every NKT API is addressed via a unique process code sent in
 * {@code @RequestParam("code")} to WorkflowEngineController.
 *
 * Naming convention:  nkt.<domain>.<operation>
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ AUTH (API 1-6)                                                          │
 * │ CUSTOMER (API 7-11)                                                     │
 * │ DISCOVER (API 12-14)                                                    │
 * │ STORES (API 15-19)                                                      │
 * │ CART / ORDER (API 20-26)                                                │
 * │ PAYMENT (API 27-29)                                                     │
 * │ WISHLIST (API 30-32)                                                    │
 * │ NOTIFICATION (API 33)                                                   │
 * │ LOCATION (API 34-35)                                                    │
 * │ STORE PROFILE (API 36-39)                                               │
 * │ STOCK (API 40-46)                                                       │
 * │ STORE ORDER (API 47-51)                                                 │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
public final class NktProcessCodes {

	private NktProcessCodes() {
	}

	// ── AUTH ──────────────────────────────────────────────────────────────────
	/** API 1 – Send OTP */
	public static final String AUTH_SEND_OTP = "nkt.auth.send_otp";
	/** API 2 – Verify OTP & issue JWT */
	public static final String AUTH_VERIFY_OTP = "nkt.auth.verify_otp";
	/** API 3 – Refresh access token */
	public static final String AUTH_REFRESH_TOKEN = "nkt.auth.refresh_token";
	/** API 4 – Enrol biometric token */
	public static final String AUTH_ENROL_BIOMETRIC = "nkt.auth.enrol_biometric";
	/** API 5 – Sign in via biometric */
	public static final String AUTH_VERIFY_BIOMETRIC = "nkt.auth.verify_biometric";
	/** API 6 – Logout */
	public static final String AUTH_LOGOUT = "nkt.auth.logout";

	// ── CUSTOMER ──────────────────────────────────────────────────────────────
	/** API 7 – Get customer profile */
	public static final String CUSTOMER_GET_PROFILE = "nkt.customer.get_profile";
	/** API 8 – Update customer profile */
	public static final String CUSTOMER_UPDATE_PROFILE = "nkt.customer.update_profile";
	/** API 9 – Add delivery address */
	public static final String CUSTOMER_ADD_ADDRESS = "nkt.customer.add_address";
	/** API 10 – Delete saved address */
	public static final String CUSTOMER_DELETE_ADDRESS = "nkt.customer.delete_address";
	/** API 11 – Toggle favourite store */
	public static final String CUSTOMER_TOGGLE_FAV = "nkt.customer.toggle_favourite";

	// ── DISCOVER ──────────────────────────────────────────────────────────────
	/** API 12 – List all categories */
	public static final String DISCOVER_CATEGORIES = "nkt.discover.categories";
	/** API 13 – Get hero banners */
	public static final String DISCOVER_BANNERS = "nkt.discover.banners";
	/** API 14 – Get nearby stores strip */
	public static final String DISCOVER_NEARBY_STORES = "nkt.discover.nearby_stores";

	// ── STORES ────────────────────────────────────────────────────────────────
	/** API 15 – List stores by category */
	public static final String STORES_LIST_BY_CATEGORY = "nkt.stores.list_by_category";
	/** API 16 – Get store details */
	public static final String STORES_GET_DETAILS = "nkt.stores.get_details";
	/** API 17 – Get store product catalogue */
	public static final String STORES_GET_PRODUCTS = "nkt.stores.get_products";
	/** API 18 – Get store services */
	public static final String STORES_GET_SERVICES = "nkt.stores.get_services";
	/** API 19 – Get available time slots */
	public static final String STORES_GET_AVAILABILITY = "nkt.stores.get_availability";

	// ── CART / ORDER (CUSTOMER) ───────────────────────────────────────────────
	/** API 20 – Validate cart */
	public static final String ORDER_VALIDATE_CART = "nkt.order.validate_cart";
	/** API 21 – Place new order */
	public static final String ORDER_PLACE = "nkt.order.place";
	/** API 22 – Get customer order history */
	public static final String ORDER_HISTORY = "nkt.order.history";
	/** API 23 – Get order detail */
	public static final String ORDER_GET_DETAIL = "nkt.order.get_detail";
	/** API 24 – Real-time order tracking (REST poll) */
	public static final String ORDER_TRACK = "nkt.order.track";
	/** API 25 – Cancel order */
	public static final String ORDER_CANCEL = "nkt.order.cancel";
	/** API 26 – Rate order & store */
	public static final String ORDER_RATE = "nkt.order.rate";

	// ── PAYMENT ───────────────────────────────────────────────────────────────
	/** API 27 – Initiate payment */
	public static final String PAYMENT_INITIATE = "nkt.payment.initiate";
	/** API 28 – Check payment status */
	public static final String PAYMENT_STATUS = "nkt.payment.status";
	/** API 29 – Payment gateway webhook */
	public static final String PAYMENT_WEBHOOK = "nkt.payment.webhook";

	// ── WISHLIST ──────────────────────────────────────────────────────────────
	/** API 30 – Get wishlist */
	public static final String WISHLIST_GET = "nkt.wishlist.get";
	/** API 31 – Add item to wishlist */
	public static final String WISHLIST_ADD = "nkt.wishlist.add";
	/** API 32 – Remove item from wishlist */
	public static final String WISHLIST_REMOVE = "nkt.wishlist.remove";

	// ── NOTIFICATION ──────────────────────────────────────────────────────────
	/** API 33 – Register device for push notifications */
	public static final String NOTIFICATION_REGISTER_DEVICE = "nkt.notification.register_device";

	// ── LOCATION ──────────────────────────────────────────────────────────────
	/** API 34 – Reverse geocode */
	public static final String LOCATION_REVERSE_GEOCODE = "nkt.location.reverse_geocode";
	/** API 35 – Global search */
	public static final String LOCATION_GLOBAL_SEARCH = "nkt.location.global_search";

	// ── STORE PROFILE (STORE OWNER) ───────────────────────────────────────────
	/** API 36 – Get store profile */
	public static final String STORE_PROFILE_GET = "nkt.store_profile.get";
	/** API 37 – Update store profile */
	public static final String STORE_PROFILE_UPDATE = "nkt.store_profile.update";
	/** API 38 – Toggle store open/closed */
	public static final String STORE_PROFILE_TOGGLE = "nkt.store_profile.toggle_status";
	/** API 39 – Get dashboard stats */
	public static final String STORE_PROFILE_DASHBOARD = "nkt.store_profile.dashboard";

	// ── STOCK ─────────────────────────────────────────────────────────────────
	/** API 40 – Get full stock list */
	public static final String STOCK_LIST = "nkt.stock.list";
	/** API 41 – Add new stock item */
	public static final String STOCK_ADD = "nkt.stock.add";
	/** API 42 – List stock sub-categories */
	public static final String STOCK_CATEGORIES = "nkt.stock.categories";
	/** API 43 – Update stock item (full update) */
	public static final String STOCK_UPDATE = "nkt.stock.update";
	/** API 44 – Adjust stock quantity (delta) */
	public static final String STOCK_ADJUST_QTY = "nkt.stock.adjust_qty";
	/** API 45 – Toggle item availability */
	public static final String STOCK_TOGGLE_AVAILABILITY = "nkt.stock.toggle_availability";
	/** API 46 – Remove custom stock item */
	public static final String STOCK_DELETE = "nkt.stock.delete";

	// ── STORE ORDER (STORE OWNER) ─────────────────────────────────────────────
	/** API 47 – Get all store orders */
	public static final String STORE_ORDER_LIST = "nkt.store_order.list";
	/** API 48 – Accept order */
	public static final String STORE_ORDER_ACCEPT = "nkt.store_order.accept";
	/** API 49 – Reject order */
	public static final String STORE_ORDER_REJECT = "nkt.store_order.reject";
	/** API 50 – Dispatch order */
	public static final String STORE_ORDER_DISPATCH = "nkt.store_order.dispatch";
	/** API 51 – Deliver order */
	public static final String STORE_ORDER_DELIVER = "nkt.store_order.deliver";
}
