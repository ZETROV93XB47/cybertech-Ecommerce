package com.novatech.cybertech.constants;

public class CyberTechAppConstants {

    public static final String REGISTRATION_CONTROLLER_BASE_PATH = "/register";

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String API_BASE_PATH = "/api/v1";

    public static final String USER_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/user";
    public static final String PRODUCT_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/product";
    public static final String CART_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/cart";
    public static final String USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/admin/user";
    public static final String PRODUCT_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/admin/management/product";
    public static final String ORDER_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/order";
    public static final String REVIEW_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/review";
    public static final String BANK_CARD_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/bank-card";
    public static final String USER_WISHLIST_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/wishlist";
    public static final String USER_EVENT_INGESTION_BASE_PATH = API_BASE_PATH + "/events";
    public static final String STRIPE_WEBHOOKS_BASE_PATH = API_BASE_PATH + "/webhooks/stripe";

    public static final String ORDER_MANAGEMENT_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/management/order";

    /**
     * Frontend-gap #2 — admin-side paginated order listing controller base path.
     * Mirrors the {@code USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH} convention.
     */
    public static final String ORDER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/admin/management/order";

    public static final String DISCOUNT_ADMIN_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/admin/discounts";
    public static final String DISCOUNT_PUBLIC_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/discounts";

    public static final String ORDER_SUMMARY_REPORT_JOB = "ORDER_SUMMARY_REPORT_JOB";

    public static final String NO_ORDERS_TO_CANCEL = "NO_ORDERS_TO_CANCEL";
    public static final String PENDING_ORDERS_MAP_BY_USER_EMAIL = "PENDING_ORDERS_MAP_BY_USER_EMAIL";
    public static final String FAILED_PAYMENT_ORDERS_MAP_BY_USERS = "FAILED_PAYMENT_ORDERS_MAP_BY_USERS";
    public static final String NO_FAILED_PAYMENT_ORDER_FOUND = "NO_FAILED_PAYMENT_ORDER_FOUND";

    public static final String REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB =  "REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB";
    public static final String CLEAN_UP_EXPIRED_STOCK_JOB = "CLEAN_UP_EXPIRED_STOCK_JOB";
    public static final String REDELIVER_FAILED_NOTIFICATIONS_JOB = "REDELIVER_FAILED_NOTIFICATIONS_JOB";


    public static final String APP_API_VERSION = "1.0";

    public static final String APPLICATION_ASYNC_TASK_EXECUTOR = "applicationAsyncTaskExecutor";

    public static final String RESERVATION_KEY_PREFIX = "reservation:order:";

    // Pagination defaults. Used by @PageableDefault — the global hard cap lives in application.properties
    // (spring.data.web.pageable.max-page-size). Per-endpoint size defaults are per-resource for UX.
    public static final int DEFAULT_PAGE_SIZE_ADMIN = 20;
    public static final int DEFAULT_PAGE_SIZE_BANK_CARD = 10;
    public static final int DEFAULT_PAGE_SIZE_WISHLIST = 20;
    public static final int DEFAULT_PAGE_SIZE_BEST_SELLERS = 15;
    public static final String DEFAULT_SORT_FIELD = "createdAt";
}
