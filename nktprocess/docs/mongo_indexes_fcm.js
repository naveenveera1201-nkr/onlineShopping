// ─────────────────────────────────────────────────────────────────────────
// MongoDB indexes for the FCM push-notification integration.
// Run once against the nktdb database (mongosh nktdb < mongo_indexes_fcm.js),
// or paste into MongoDB Compass / Atlas "Shell".
// ─────────────────────────────────────────────────────────────────────────

// user_devices — one document per (userId, deviceId); this is the pair the
// FCM_REGISTER_DEVICE handler upserts on, so it must be unique.
db.user_devices.createIndex(
  { userId: 1, deviceId: 1 },
  { unique: true, name: "uniq_user_device" }
);

// Fast lookup of "all active devices for this user" (FCM_SEND_NOTIFICATION,
// order notifications).
db.user_devices.createIndex(
  { userId: 1, isActive: 1 },
  { name: "idx_user_active" }
);

// Fast lookup of "all active employee/business devices for this store"
// (FCM_SEND_GROUP_NOTIFICATION: STORE_STAFF / STORE_OWNER / STORE).
db.user_devices.createIndex(
  { storeId: 1, userType: 1, isActive: 1 },
  { name: "idx_store_usertype_active" }
);

// Fast lookup of "all active customer devices" (FCM_SEND_GROUP_NOTIFICATION:
// CUSTOMERS broadcast).
db.user_devices.createIndex(
  { userType: 1, isActive: 1 },
  { name: "idx_usertype_active" }
);

// Deactivating a dead token by its value (invalid-token handling).
db.user_devices.createIndex(
  { fcmToken: 1 },
  { name: "idx_fcm_token" }
);

// notifications (optional history collection) — recent-first per user, and
// a lookup path for a future in-app notification screen.
db.notifications.createIndex(
  { userId: 1, createdAt: -1 },
  { name: "idx_user_recent" }
);
db.notifications.createIndex(
  { storeId: 1, createdAt: -1 },
  { name: "idx_store_recent", sparse: true }
);
