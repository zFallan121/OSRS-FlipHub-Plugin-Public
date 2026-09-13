package com.osrsfliphub;

/**
 * FlipHub answered and refused the request.
 */
final class ApiRefusedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    ApiRefusedException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    int getStatusCode() {
        return statusCode;
    }

    /** Whether the server is saying the request itself is wrong, rather than asking for patience. */
    boolean isRefusalOfTheRequest() {
        return statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429;
    }

    /**
     * Whether this failure, or anything it wraps, is FlipHub refusing the request outright.
     */
    static boolean refusedTheRequest(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiRefusedException) {
                return ((ApiRefusedException) current).isRefusalOfTheRequest();
            }
            current = current.getCause();
        }
        return false;
    }
}
