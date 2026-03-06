package in.virit.meater.cloud;

public class MeaterCloudException extends Exception {

    private final int statusCode;

    public MeaterCloudException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public MeaterCloudException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public MeaterCloudException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
