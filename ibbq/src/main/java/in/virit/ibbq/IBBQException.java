package in.virit.ibbq;

public class IBBQException extends Exception {

    public IBBQException(String message) {
        super(message);
    }

    public IBBQException(String message, Throwable cause) {
        super(message, cause);
    }
}
