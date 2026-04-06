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

    public static final String ORDER_SUMMARY_REPORT_JOB = "ORDER_SUMMARY_REPORT_JOB";

    public static final String NO_ORDERS_TO_CANCEL = "NO_ORDERS_TO_CANCEL";
    public static final String PENDING_ORDERS_MAP_BY_USER_EMAIL = "PENDING_ORDERS_MAP_BY_USER_EMAIL";
    public static final String FAILED_PAYMENT_ORDERS_MAP_BY_USERS = "FAILED_PAYMENT_ORDERS_MAP_BY_USERS";
    public static final String NO_FAILED_PAYMENT_ORDER_FOUND = "NO_FAILED_PAYMENT_ORDER_FOUND";

    public static final String REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB =  "REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB";
    public static final String CLEAN_UP_EXPIRED_STOCK_JOB = "CLEAN_UP_EXPIRED_STOCK_JOB";


    public static final Integer NUMBER_OF_MOST_SELLED_PRODUCTS_TO_GET = 15;


    public static final String APP_API_VERSION = "1.0";

    public static final String APPLICATION_ASYNC_TASK_EXECUTOR = "applicationAsyncTaskExecutor";
}
