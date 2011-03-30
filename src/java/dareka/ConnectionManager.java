package dareka;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Config;
import dareka.common.HttpIOException;
import dareka.common.Logger;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;

public class ConnectionManager extends Observable implements Runnable {
    private Socket browser;
    private Config config;
    private String processingURI;
    private volatile Resource processingResource;
    private volatile boolean stopped = false;

    private List<ProcessorEntry> processorEntries =
            new ArrayList<ProcessorEntry>();

    public ConnectionManager(Config config, Socket browser) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (browser == null) {
            throw new IllegalArgumentException("browser must not be null");
        }

        this.config = config;
        this.browser = browser;
    }

    public void run() {
        try {
            while (processAPairOfMessages()) {
                // loop until the method returns false.
                processingURI = null;
            }
            Logger.debugWithThread("loop end");
        } catch (ConnectException e) {
            // アウトバウンド側に接続失敗
            Logger.debugWithThread(e);
            printWarning(e);
        } catch (SocketException e) {
            Logger.debugWithThread(e);

            // Connection reset はよくあるので通常はログに出さない
            if (!isConnectionReset(e)) {
                printWarning(e);
            }
        } catch (HttpIOException e) {
            Logger.debugWithThread(e);
        } catch (IOException e) {
            // NIOを使っているとConnection resetではなく以下のメッセージを持った
            // IOExceptionになる。
            // 「既存の接続はリモート ホストに強制的に切断されました。」
            // 「確立された接続がホスト コンピュータのソウトウェアによって中止されました。」
            // UnknownHostExceptionも通常ログには出さない
            Logger.debugWithThread(e);
        } catch (CancelledKeyException e) {
            // read()中に別スレッドからstop()で読み込みを中断させると
            // CancelledKeyExceptionになる。
            // ただしJava実行環境の実装依存。
            // 意図したエラーなのでログには出さない。
            Logger.debugWithThread(e);
        } catch (ClosedSelectorException e) {
            // 別スレッドからstop()でSelectorをclose()すると
            // ClosedSelectorExceptionになる。
            // 意図したエラーなのでログには出さない。
            Logger.debugWithThread(e);
        } catch (Exception e) {
            Logger.debugWithThread(e);
            printWarning(e);
        } finally {
            if (!browser.isClosed()) {
                consumeBrowserInput();
                CloseUtil.close(browser);
            }

            notifyCompletion();
        }
    }

    private void printWarning(Exception e) {
        Logger.warning("failed to process: " + processingURI + "\n\t"
                + e.toString());
    }

    private boolean isConnectionReset(SocketException e) {
        // この判定方法でいいかは…
        return e.getMessage().startsWith("Connection reset")
                || e.getMessage().startsWith("Software caused connection abort");
    }

    /**
     * 余分なデータを送ってくるブラウザ対策。
     */
    private void consumeBrowserInput() {
        try {
            // java.net APIで処理する。
            // そのために非ブロックモードを解除。
            SocketChannel bc = browser.getChannel();
            // bcはnullにはならない。
            bc.configureBlocking(true);

            // 次の読み込みでデッドロックを避けるため出力は停止。
            // (FINを送信)
            browser.shutdownOutput();

            // IEはPOSTリクエストの時に余分にCRLFを送って来ているので
            // IEが送信を正常終了できるように読み飛ばしてやる。
            // (FINの受信まで待つ)
            // これを読み飛ばしてやらないでSocket#close()すると
            // 「メッセージサーバーに接続できませんでした。」などになる
            // 参考: IEが余分なCRLFを送信することについて触れられ
            // ている公式の文書
            // http://support.microsoft.com/kb/823099/
            while (browser.getInputStream().read() != -1) {
                // no nothing
            }
        } catch (Exception e) {
            // IOExceptionのConnection reset系のエラーやCancelledKeyExceptionが
            // 来る。
            // 処理中でresetされていた場合はここでもまた例外になるが、
            // 実際にread()してみないと区別が付かないので仕方ない。
            Logger.debugWithThread(e.toString() + "(consuming)");
        }
    }

    private boolean processAPairOfMessages() throws IOException {
        HttpRequestHeader requestHeader =
                new HttpRequestHeader(browser.getInputStream());
        processingURI = requestHeader.getURI();

        Logger.debugWithThread(requestHeader.getMethod() + " "
                + requestHeader.getURI());

        // [nl] 設定ファイルの更新チェック
        // processAPairOfMessages()としての振舞いではないが
        // new HttpRequestHeaderは典型的なブロック場所なので
        // その直後に書くことにする。
        if (config.reload()) {
            Logger.info("Reloading '" + config.getConfigFile().getName() + "'");
        }

        // 対応するProcessorを探して処理
        for (ProcessorEntry entry : processorEntries) {
            if (isMatchToEntry(entry, requestHeader)) {
                boolean canContinue =
                        useProcessor(requestHeader, entry.getProcessor());
                Logger.debugWithThread("end");
                return canContinue;
            }
        }

        throw new HttpIOException("request cannot be processed:\r\n"
                + requestHeader);
    }

    private boolean useProcessor(HttpRequestHeader requestHeader,
            Processor processor) throws IOException {
        processingResource = processor.onRequest(requestHeader);

        if (stopped) {
            // この停止要求チェックはprocessingResource取得より
            // 後に無ければならない
            return false;
        }

        if (processingResource == null) {
            throw new HttpIOException(
                    "request processor failed to handle request:\r\n"
                            + requestHeader);
        }

        try { // ensure (processingResource == null) after the transfer.
            requestHeader.removeHopByHopHeaders();
            return processingResource.transferTo(browser, requestHeader, config);
        } finally {
            processingResource = null;
        }
    }

    private boolean isMatchToEntry(ProcessorEntry entry,
            HttpRequestHeader requestHeader) {
        if (entry.getMethod() != null) {
            if (!entry.getMethod().equals(requestHeader.getMethod())) {
                return false;
            }
        }

        if (entry.getUri() != null) {
            Matcher m = entry.getUri().matcher(requestHeader.getURI());
            if (!m.lookingAt()) {
                return false;
            }
            // TODO マッチ結果をProcessorで再利用できるようにして高速化
        }

        return true;
    }

    private void notifyCompletion() {
        setChanged();
        notifyObservers();
    }

    public void addProcessor(String method, Pattern url, Processor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor must not be null");
        }

        ProcessorEntry entry = new ProcessorEntry(method, url, processor);

        processorEntries.add(entry);
    }

    /**
     * stop blocking operation.
     */
    public void stop() {
        stopped = true;

        CloseUtil.close(browser);

        try {
            processingResource.stopTransfer();
        } catch (NullPointerException npe) {
            // processingResourceの書き換えは別スレッドで行われるので
            // nullチェックするなら呼び出しをアトミックに行わなければならないが
            // それだとロック管理が複雑になるのでoptimisticに行う
            // nullだった場合は既に転送終了しているのでOK
            Logger.debugWithThread(npe);
        }
    }
}
