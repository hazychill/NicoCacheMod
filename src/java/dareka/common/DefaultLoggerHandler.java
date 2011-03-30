package dareka.common;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DefaultLoggerHandler implements LoggerHandler {
    private static final boolean DEBUG = Boolean.getBoolean("dareka.debug");

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#debug(java.lang.String)
     */
    public void debug(String message) {
        if (DEBUG) {
            System.out.println("DEBUG: " + message);
        }
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#debug(java.lang.Throwable)
     */
    public void debug(Throwable t) {
        if (DEBUG) {
            debug(getStackTraceString(t));
        }
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#debugWithThread(java.lang.String)
     */
    public void debugWithThread(String message) {
        if (DEBUG) {
            debug(Thread.currentThread().getName() + ": " + message);
        }
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#debugWithThread(java.lang.Throwable)
     */
    public void debugWithThread(Throwable t) {
        if (DEBUG) {
            debugWithThread(getStackTraceString(t));
        }
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#info(java.lang.String)
     */
    public void info(String message) {
        System.out.println(message);
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#info(java.lang.String, java.lang.Object)
     */
    public void info(String format, Object... arg) {
        info(String.format(format, arg));
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#warning(java.lang.String)
     */
    public void warning(String message) {
        System.out.println(message);
    }

    /* (”ñ Javadoc)
     * @see dareka.common.LoggerHandler2#error(java.lang.Throwable)
     */
    public void error(Throwable t) {
        System.out.println(getStackTraceString(t));
    }

    protected String getStackTraceString(Throwable t) {
        if (t == null) {
            return "null";
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
