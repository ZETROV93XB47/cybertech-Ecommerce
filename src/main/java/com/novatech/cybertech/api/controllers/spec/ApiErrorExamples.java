package com.novatech.cybertech.api.controllers.spec;

/**
 * Error-body examples shared by the {@code *ApiSpec} interfaces. Compile-time constants, as
 * {@code @ExampleObject#value} requires.
 */
public final class ApiErrorExamples {

    public static final String NOT_ENOUGH_STOCK_DESCRIPTION =
            "Not enough stock (NOT_ENOUGH_STOCK): details.items lists the product lines in shortage";

    public static final String NOT_ENOUGH_STOCK_EXAMPLE_NAME = "NOT_ENOUGH_STOCK";

    public static final String NOT_ENOUGH_STOCK_EXAMPLE = """
            {
              "message": "Not enough stock for product 0199c6a1-3f2b-7c4e-9d10-5b8a2e4f6c01: requested=3, available=1",
              "httpStatusCode": 409,
              "errorCodeType": "FUNCTIONAL",
              "errorCode": "NOT_ENOUGH_STOCK",
              "details": {
                "items": [
                  { "productUuid": "0199c6a1-3f2b-7c4e-9d10-5b8a2e4f6c01", "requested": 3, "available": 1 }
                ]
              }
            }
            """;

    private ApiErrorExamples() {
    }
}
