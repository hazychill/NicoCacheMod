package dareka;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.util.Collections;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Config;
import dareka.common.Logger;
import dareka.processor.Processor;
import dareka.processor.impl.ConnectProcessor;
import dareka.processor.impl.GetPostProcessor;
import dareka.processor.impl.NicoCachingProcessor;
import dareka.processor.impl.NicoRecordingUrlProcessor;
import dareka.processor.impl.NicoRecordingWatchProcessor;
import dareka.processor.impl.SaveCommentProcessor;

public class Server implements Observer {
    private static final int MAX_WAITING_TIME = 10;

    private Config config;
    private ServerSocket serverSocket;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private Set<ConnectionManager> liveWorkers =
            Collections.synchronizedSet(new HashSet<ConnectionManager>());
    private volatile boolean stopped = false;

    // they are able to be shared among threads.
    private Processor connectProcessor = new ConnectProcessor();
    private Processor getPostProcessor = new GetPostProcessor();
    private Processor nicoRecordingWatchProcessor =
            new NicoRecordingWatchProcessor();
    private Processor nicoRecordingUrlProcessor =
            new NicoRecordingUrlProcessor();

    public Server(Config config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        this.config = config;

        // use channel to make it available Socket#getChannel() for non blocking
        // I/O.
        ServerSocketChannel serverCh = ServerSocketChannel.open();
        serverSocket = serverCh.socket();
    }

    /**
     * Receive an event which indicates completion of worker.
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    public void update(Observable o, Object arg) {
        synchronized (liveWorkers) {
            if (liveWorkers.remove(o) == false) {
                Logger.warning("internal error: live worker mismatch");
            }
            if (stopped) {
                // This message may be printed before finalizing, but
                // decided not to make its own flag because this is
                // just looking issue.
                Logger.info("remaining worker=" + liveWorkers.size());
            }
        }
    }

    /**
     * Start the server. The thread which call this method is blocked until
     * stop() is called or some errors occurred.
     */
    public void start() {
        if (stopped) {
            return;
        }

        try { // ensure cleanup
            bindServerSocket();
            acceptServerSocket();
        } finally {
            Logger.info("finalizing");
            Logger.debugWithThread("stopping accepting request");
            cleanupServerSocket();
            Logger.debugWithThread("stopping processing request");
            cleanupWorkers();
            Logger.debugWithThread("stopping threads");
            cleanupExecutor();
            Logger.info("finalized");

            if (liveWorkers.size() > 0) {
                Logger.warning("internal error: remaining live workers: "
                        + liveWorkers.size());
            }
        }
    }

    /**
     * Stop the server. Please call this method from another thread which called
     * start().
     */
    public synchronized void stop() {
        stopped = true;

        if (!serverSocket.isClosed()) {
            CloseUtil.close(serverSocket);
        }
        executor.shutdown();
    }

    private void bindServerSocket() {
        try {
            serverSocket.bind(new InetSocketAddress(
                    InetAddress.getByName(null), Integer.getInteger(
                            "listenPort").intValue()));
        } catch (IOException e) {
            Logger.error(e);
            stop();
        }
    }

    private void acceptServerSocket() {
        try {
            boolean timeoutSupportedOrUnknown = true;
            int timeout = Integer.getInteger("readTimeout").intValue();

            while (!stopped) {
                Socket client = serverSocket.accept();

                try { // ensure client.close() even in errors.
                    if (timeoutSupportedOrUnknown) {
                        client.setSoTimeout(timeout);
                        if (client.getSoTimeout() != timeout) {
                            Logger.warning("read timeout is not supported");
                            timeoutSupportedOrUnknown = false;
                        }
                    }

                    synchronized (this) { // avoid conflicting with stop()
                        if (stopped) {
                            break;
                        }

                        ConnectionManager worker;
                        worker = new ConnectionManager(config, client);
                        // TODO コーディングレスで登録できるようにする。

                        registerProcessor(new NicoCachingProcessor(executor),
                                worker);

                        registerProcessor(nicoRecordingUrlProcessor, worker);
                        registerProcessor(nicoRecordingWatchProcessor, worker);
                        registerProcessor(new SaveCommentProcessor(), worker);
                        registerProcessor(getPostProcessor, worker);
                        registerProcessor(connectProcessor, worker);

                        // Observation must be prepared before call execute()
                        // to avoid loss of event in case of immediate
                        // complete
                        prepareObservation(worker);

                        executor.execute(worker);
                        // for debug
                        //new Thread(worker).start();
                    }
                } catch (Exception e) {
                    Logger.error(e);
                    CloseUtil.close(client);
                }
            }
        } catch (IOException e) {
            // stop() is called.
            // including AsynchronousCloseException (in NIO)
            Logger.debug(e);
        }
    }

    private void registerProcessor(Processor processor, ConnectionManager worker) {
        Pattern p = processor.getSupportedURLAsPattern();
        if (p == null) {
            String url = processor.getSupportedURLAsString();
            if (url != null) {
                p = Pattern.compile(url, Pattern.LITERAL);
            }
        }

        String[] methods = processor.getSupportedMethods();
        if (methods == null) {
            return;
        }

        for (String method : methods) {
            worker.addProcessor(method, p, processor);
        }
    }

    private void prepareObservation(ConnectionManager worker) {
        liveWorkers.add(worker);
        worker.addObserver(this);
    }

    private void cleanupServerSocket() {
        if (!serverSocket.isClosed()) {
            CloseUtil.close(serverSocket);
        }
    }

    private void cleanupWorkers() {
        synchronized (liveWorkers) {
            for (ConnectionManager worker : liveWorkers) {
                worker.stop();
            }
        }
    }

    private void cleanupExecutor() {
        for (int i = 0; i < 10 && !executor.isTerminated(); ++i) {
            try {
                Logger.debug("waiting for terminating threads...");
                executor.shutdownNow();
                if (executor.awaitTermination(MAX_WAITING_TIME,
                        TimeUnit.SECONDS)) {
                    Logger.debug("done");
                    break;
                } else {
                    Logger.debug("timed out");
                }
            } catch (InterruptedException e) {
                Logger.warning(e.toString());
            }
        }
    }
}
