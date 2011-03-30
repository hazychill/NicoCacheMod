package dareka.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dareka.common.CloseUtil;
import dareka.common.Config;
import dareka.common.HttpIOException;

/**
 * Abstraction of resources and how they are transferred.
 *
 */
public abstract class Resource {
    /**
     * Represents resource to be sent.
     *
     */
    public enum Type {
        URL, STRING, HOSTPORT
    }

    protected static final int BUF_SIZE = 32 * 1024;

    private volatile boolean stopped = false;
    private volatile InputStream sendingIn;

    private Set<TransferListener> listeners = new HashSet<TransferListener>();
    private HttpMessageHeaderHolder explicitHeaders =
        new HttpMessageHeaderHolder();
    private boolean onTransferEndFired = false;

    /**
     * Factory to create resource object. This is provided for convenience.
     *
     * @param type
     * @param resource
     * @return instance of Resource corresponding specified type.
     * @throws IOException
     */
    public static Resource get(Type type, String resource) throws IOException {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }

        if (type == Type.URL) {
            return new URLResource(resource);
        } else if (type == Type.STRING) {
            return new StringResource(resource);
        } else if (type == Type.HOSTPORT) {
            return new HostportResource(resource);
        } else {
            throw new IllegalArgumentException("invalid type: " + type);
        }
    }

    public void addTransferListener(TransferListener l) {
        if (l == null) {
            throw new IllegalArgumentException(
            "TransferListener must not be null");
        }

        listeners.add(l);
    }

    public void setResponseHeader(String key, String value) {
        explicitHeaders.put(key, value);
    }

    public void addResponseHeader(String key, String value) {
        explicitHeaders.add(key, value);
    }

    /**
     * Transfer this resource to specified receiver.
     *
     * @param receiver
     * @param requestHeader
     * @param config
     * @return true means it is possible to continue using the receiver.
     * @throws IOException
     */
    public boolean transferTo(Socket receiver, HttpRequestHeader requestHeader,
            Config config) throws IOException {
        try {
            return endEnsuredTransferTo(receiver, requestHeader, config);
        } finally {
            // notify the end to listeners in case of error.
            // If fireOnTransferEnd(true) is already called,
            // this affects nothing.
            fireOnTransferEnd(false);
        }
    }

    /**
     * Stop transfer.
     * {@link Resource#isStopped()} returns true after this method.
     * Subclass must implements its own stopping logic for resource.
     * This method expected to be called from another thread besides
     * thread which call {@link Resource#transferTo(Socket, HttpRequestHeader, Config)}
     */
    public void stopTransfer() {
        stopped = true;
        CloseUtil.close(sendingIn);
    }

    public boolean isStopped() {
        return stopped;
    }

    /**
     * Transfer this resource to specified receiver. It is ensured that
     * onTransferEnd event is fired in case of error.
     *
     * <p>
     * Concrete class must override either transferTo() or
     * protectedTransferTo().
     *
     * @param receiver
     * @param requestHeader
     * @param config
     * @return true means it is possible to continue using the receiver.
     * @throws IOException
     */
    protected boolean endEnsuredTransferTo(Socket receiver,
            HttpRequestHeader requestHeader, Config config) throws IOException {
        return false;
    }

    protected int getListenersSize() {
        return listeners.size();
    }

    protected void fireOnResponseHeader(HttpResponseHeader responseHeader) {
        for (TransferListener l : listeners) {
            l.onResponseHeader(responseHeader);
        }
    }

    protected void fireOnTransferBegin(OutputStream receiverOut) {
        for (TransferListener l : listeners) {
            l.onTransferBegin(receiverOut);
        }
    }

    protected void fireOnTransferring(byte[] buf, int length) {
        for (TransferListener l : listeners) {
            l.onTransferring(buf, length);
        }
    }

    protected void fireOnTransferEnd(boolean completed) {
        if (onTransferEndFired) {
            return;
        }
        onTransferEndFired = true;

        for (TransferListener l : listeners) {
            l.onTransferEnd(completed);
        }
    }

    /**
     * Execute a set of methods for sending response header. This method is
     * intended to keep code away from mistake, in particular, event handling.
     *
     * This method fire onResponseHeader event.
     *
     * @param out receiver of responseHeader.
     * @param responseHeader response header.
     * @throws IOException
     */
    protected void execSendingHeaderSequence(OutputStream out,
            HttpResponseHeader responseHeader) throws IOException {
        overrideResponseMessage(responseHeader);
        doSetMandatoryResponseHeader(responseHeader);
        fireOnResponseHeader(responseHeader);
        HttpUtil.sendHeader(out, responseHeader);
    }

    /**
     * Execute a set of methods for sending response body. This method is
     * intended to keep code away from mistake, in particular, event handling.
     *
     * This method fire onTransferXXX events.
     *
     * @param out receiver of body
     * @param in source of body
     * @param contentLength expected content length of source.
     * @throws IOException
     */
    protected void execSendingBodySequence(OutputStream out, InputStream in,
            long contentLength) throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        int len;
        long transferredLength = 0;

        sendingIn = in;
        try { // ensure (sendingIn == null) when exiting
            if (isStopped()) {
                throw new HttpIOException("transfer stopped");
            }

            // split for performance
            if (getListenersSize() == 0) {
                while ((len = sendingIn.read(buf)) != -1) {
                    try {
                        out.write(buf, 0, len);
                        transferredLength += len;
                    } catch (IOException e) {
                        // bad performance, but there is no chance to know
                        // write() is failed (not read()).
                        CloseUtil.close(out);
                        throw e;
                    }
                }

                if (isExpectedLength(contentLength, transferredLength)) {
                    // do nothing
                } else {
                    // some sites (ex. rcm-jp.amazon.co.jp) return
                    // wrong content length. In this case, the connection
                    // between browser must be disconnected to show the end.
                    throw new HttpIOException(
                            "inconsistent content length: header="
                            + contentLength + " actual="
                            + transferredLength);
                }
            } else {
                fireOnTransferBegin(out);

                while ((len = sendingIn.read(buf)) != -1) {
                    fireOnTransferring(buf, len);

                    try {
                        out.write(buf, 0, len);
                        transferredLength += len;
                    } catch (IOException e) {
                        fireOnTransferEnd(false);
                        CloseUtil.close(out);
                        throw e;
                    }
                }

                if (isExpectedLength(contentLength, transferredLength)) {
                    fireOnTransferEnd(true);
                } else {
                    // No exception is thrown in case of upper proxy disconnect
                    // and so on. To avoid making incomplete cache,
                    // check the length
                    fireOnTransferEnd(false);
                    throw new HttpIOException(
                            "inconsistent content length: header="
                            + contentLength + " actual="
                            + transferredLength);
                }
            }
        } finally {
            sendingIn = null;
        }
    }

    private boolean isExpectedLength(long contentLength, long transferredLength) {
        return transferredLength == contentLength || contentLength == -1;
    }

    private void overrideResponseMessage(HttpResponseHeader responseHeader) {
        for (Map.Entry<String, List<String>> entry : explicitHeaders.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            responseHeader.removeMessageHeader(key);
            for (String value : values) {
                responseHeader.addMessageHeader(key, value);
            }
        }
    }

    /**
     * Set mandatory headers such as Content-Length.
     * Subclasses implement here if necessary. (template method pattern)
     *
     * @param responseHeader
     */
    protected void doSetMandatoryResponseHeader(
            HttpResponseHeader responseHeader) {
        // do nothing
    }

    protected boolean isClientCanKeepAlive(HttpRequestHeader requestHeader) {
        String connection =
            requestHeader.getMessageHeader(HttpHeader.CONNECTION);
        if (requestHeader.getVersion().equals("HTTP/1.0")) {
            if (connection == null) {
                return false;
            }
        } else {
            // assume HTTP/1.1 client
            if (connection != null
                    && connection.equalsIgnoreCase(HttpHeader.CONNECTION_CLOSE)) {
                return false;
            }
        }
        return true;
    }

}
