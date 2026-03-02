package com.novatech.cybertech.constants;

public class CyberTechAppConstants {

    public static final String REGISTRATION_CONTROLLER_BASE_PATH = "/register";

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String API_BASE_PATH = "/api/v1";

    public static final String USER_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/user";
    public static final String PRODUCT_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/product";
    public static final String CART_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/cart";
    public static final String USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH = "/api/v1/services/admin/user";
    public static final String PRODUCT_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH = "/api/v1/services/admin/management/product";
    public static final String ORDER_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/order";
    public static final String REVIEW_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/review";
    public static final String BANK_CARD_CRUD_CONTROLLER_BASE_PATH = "/api/v1/services/bank-card";
    public static final String USER_WISHLIST_CONTROLLER_BASE_PATH = "/api/v1/services/wishlist";
    public static final String USER_EVENT_INGESTION_BASE_PATH = "/api/v1/events";
    public static final String STRIPE_WEBHOOKS_BASE_PATH = "/api/v1/webhooks/stripe";

    public static final String ORDER_MANAGEMENT_CONTROLLER_BASE_PATH = "/api/v1/services/management/order";

    public static final String ORDER_SUMMARY_REPORT_JOB = "ORDER_SUMMARY_REPORT_JOB";

    public static final String NO_ORDERS_TO_CANCEL = "NO_ORDERS_TO_CANCEL";
    public static final String PENDING_ORDERS_MAP_BY_USER_EMAIL = "PENDING_ORDERS_MAP_BY_USER_EMAIL";
    public static final String FAILED_PAYMENT_ORDERS_MAP_BY_USERS = "FAILED_PAYMENT_ORDERS_MAP_BY_USERS";
    public static final String NO_FAILED_PAYMENT_ORDER_FOUND = "NO_FAILED_PAYMENT_ORDER_FOUND";

    public static final String REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB =  "REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB";
    public static final String CLEAN_UP_EXPIRED_STOCK_JOB = "CLEAN_UP_EXPIRED_STOCK_JOB";


    public static final Integer NUMBER_OF_MOST_SELLED_PRODUCTS_TO_GET = 15;
}
