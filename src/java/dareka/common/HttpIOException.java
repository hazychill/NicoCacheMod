package dareka.common;

import java.io.IOException;

/**
 * HTTP���C���ł̓��o�͎��s��\����O�B
 *
 */
public class HttpIOException extends IOException {
    private static final long serialVersionUID = 3524846743314508104L;

    public HttpIOException() {
        super();
    }

    public HttpIOException(String message) {
        super(message);
    }

    // These methods comes from JDK6.0
//    public HttpIOException(Throwable cause) {
//        super(cause);
//    }
//
//    public HttpIOException(String message, Throwable cause) {
//        super(message, cause);
//    }

}
