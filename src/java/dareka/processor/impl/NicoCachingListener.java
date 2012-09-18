package dareka.processor.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Logger;
import dareka.processor.HttpResponseHeader;
import dareka.processor.TransferListener;

public class NicoCachingListener implements TransferListener {
    private static final int BUF_SIZE = 32 * 1024;
    private static final Pattern CONTENT_RANGE_VALUE_PATTERN =
            Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");

    private Cache cache;
    private FutureTask<String> retrieveTitleTask;
    private OutputStream out;
    private boolean errorOccured;
    private boolean keepCacheOnError = false;

    // [nl]
    InputStream cacheInput = null;
    int tmpSize = 0;
    boolean partial = false;
    boolean dupconnect = false;

    public NicoCachingListener(Cache cache,
            FutureTask<String> retrieveTitleTask, InputStream cacheInput,
            boolean dupconnect) {
        this.cache = cache;
        this.retrieveTitleTask = retrieveTitleTask;
        this.cacheInput = cacheInput;
        this.dupconnect = dupconnect;
        errorOccured = false;
    }

    // レジューム成功時はヘッダを200にする
    public void onResponseHeader(HttpResponseHeader responseHeader) {
        long contentLength = responseHeader.getContentLength();

        if (contentLength <= 0) {
            errorOccured = true;
        }

        int statusCode = responseHeader.getStatusCode();

        if (statusCode == 206) {
            String rangeValue =
                    responseHeader.getMessageHeader("Content-Range");
            Matcher m = CONTENT_RANGE_VALUE_PATTERN.matcher(rangeValue);
            if (m.find()) {
                if (Integer.parseInt(m.group(2)) + 1 == Integer.parseInt(m.group(3))) {
                    Logger.info("Partial download from " + m.group(1) + " byte");
                } else {
                    Logger.info("Maybe Bad Responce: " + m.group(0));
                }
                contentLength = Long.parseLong(m.group(3));
                Cache.setDLFlag(cache.getId(), (int) contentLength);

                // [nl] DLする部分までのサイズを設定し、部分キャッシュ送信フラグをON
                tmpSize = Integer.parseInt(m.group(1));
                partial = true;
            }
            // [nl] ヘッダを修正
            responseHeader.setStatusCode(200, "OK");
            responseHeader.removeMessageHeader("Content-Range");
            responseHeader.removeMessageHeader("Accept-Range");
            responseHeader.setContentLength(contentLength);
        } else if (statusCode != 200) {
            errorOccured = true;
        }

        try {
            if (!errorOccured && !dupconnect) {
                out = cache.getTmpOutputStream(partial);
            }
        } catch (IOException e) {
            Logger.warning(cache.getCacheFileName() + ": " + e.toString());
        }
    }

    // キャッシュに有る分を送信する
    public void onTransferBegin(OutputStream bout) {
        if (partial && cacheInput != null) {
            try {
                int len = 0;
                int rest = tmpSize;
                byte[] buf = new byte[BUF_SIZE];
                while ((len = cacheInput.read(buf)) != -1) {
                    try {
                        bout.write(buf, 0, (len < rest) ? len : rest);
                    } catch (IOException e) {
                        // ブラウザへの書き込みでエラーになった場合は
                        // キャッシュを削除しない
                        keepCacheOnError = true;
                        throw e;
                    }
                    rest -= len;
                }
            } catch (IOException e) {
                Logger.warning(cache.getCacheFileName() + ": " + e.toString());
                errorOccured = true;
            } finally {
                if (CloseUtil.close(cacheInput) == false) {
                    errorOccured = true;
                }
            }
        }
    }

    public void onTransferring(byte[] buf, int length) {
        if (errorOccured || out == null) {
            return;
        }

        try {
            out.write(buf, 0, length);
        } catch (IOException e) {
            Logger.warning(cache.getCacheFileName() + ": " + e.toString());
            errorOccured = true;
        }
    }

    public void onTransferEnd(boolean completed) {
        if (dupconnect) {
            return;
        }

        // [nl] DL中フラグを消して、キャッシュを閉じる
        Cache.setDLFlag(cache.getId(), -1);

        if (CloseUtil.close(out) == false) {
            errorOccured = true;
        }

        try {
            Wrapupper w =
                    selectWrapupper(completed, errorOccured, keepCacheOnError,
                            cache, retrieveTitleTask);
            w.wrapup();
        } catch (IOException e) {
            Logger.debugWithThread(e);
            Logger.warning(e.toString());
        }
    }

    /**
     * Select wrapping up way.
     *
     * This condition selecting is a little bit complex,
     * so make this part a method to be able to test alone.
     *
     * @param completed
     * @param aErrorOccured
     * @param aKeepCacheOnError
     * @param aCache
     * @param aRetrieveTitleTask
     * @return
     */
    static Wrapupper selectWrapupper(boolean completed, boolean aErrorOccured,
            boolean aKeepCacheOnError, Cache aCache,
            FutureTask<String> aRetrieveTitleTask) {
        if (aErrorOccured) {
            return new Cleanupper(aKeepCacheOnError, aCache, aRetrieveTitleTask);
        } else if (!completed) {
            if (Boolean.getBoolean("resumeDownload")) {
                // [nl] エラーじゃなく、単に完了してないだけなら
                return new Suspender(aCache, aRetrieveTitleTask);
            } else {
                return new Cleanupper(aKeepCacheOnError, aCache,
                        aRetrieveTitleTask);
            }
        } else { // completed
            return new Completer(aCache, aRetrieveTitleTask);
        }
    }

    static interface Wrapupper {
        void wrapup() throws IOException;
    }

    static class Cleanupper implements Wrapupper {
        private boolean keepCacheOnError;
        private Cache cache;
        private FutureTask<String> retrieveTitleTask;

        Cleanupper(boolean keepCacheOnError, Cache cache,
                FutureTask<String> retrieveTitleTask) {
            this.keepCacheOnError = keepCacheOnError;
            this.cache = cache;
            this.retrieveTitleTask = retrieveTitleTask;
        }

        public void wrapup() throws IOException {
            if (!keepCacheOnError) {
                cache.deleteTmp();
                Logger.debugWithThread(cache.getCacheFileName() + " deleted");
            }

            if (retrieveTitleTask != null) {
                retrieveTitleTask.cancel(true);
            }
        }
    }

    static class Suspender implements Wrapupper {
        private Cache cache;
        private FutureTask<String> retrieveTitleTask;

        Suspender(Cache cache, FutureTask<String> retrieveTitleTask) {
            this.cache = cache;
            this.retrieveTitleTask = retrieveTitleTask;
        }

        public void wrapup() throws IOException {
            String title;
            try {
                if (retrieveTitleTask != null
                        && (title = retrieveTitleTask.get()) != null) {
                    cache.setDescribe(title);
                    cache.setTmpDescribe(title);
                }
            } catch (Exception e) {
                Logger.warning("title retrieving failed: " + e.toString());
            }

            Logger.info("suspended           : " + cache.getCacheFileName());
        }
    }

    static class Completer implements Wrapupper {
        private Cache cache;
        private FutureTask<String> retrieveTitleTask;

        Completer(Cache cache, FutureTask<String> retrieveTitleTask) {
            this.cache = cache;
            this.retrieveTitleTask = retrieveTitleTask;
        }

        public void wrapup() throws IOException {
            String title;
            try {
                if (retrieveTitleTask != null
                        && (title = retrieveTitleTask.get()) != null) {
                    cache.setDescribe(title);
                }
            } catch (Exception e) {
                Logger.warning("title retrieving failed: " + e.toString());
            }

            cache.store();
            Logger.info("cache completed     : " + cache.getCacheFileName());
        }
    }
}
