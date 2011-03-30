package dareka.processor.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Logger;
import dareka.processor.HttpHeader;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;
import dareka.processor.impl.NicoIdInfoCache.Entry;

public class NicoCachingProcessor implements Processor {
    private static final String[] SUPPORTED_METHODS = new String[] { "GET" };
    /**
     * SMILEVIDEOの動画URLの正規表現
     */
    // http://s-clb5.smilevideo.jp/smile?v=102982.92382
    // 2009/3頃から http://smile-clb51.nicovideo.jp/smile?s=7074214.90075as3
    private static final Pattern SM_FLV_PATTERN =
            Pattern.compile("^http://[^/]+(?:smilevideo|nicovideo)\\.jp/smile\\?(\\w)=([^.]+)\\.\\d+(?:as3)?(low)?$");

    private Executor executor;

    public NicoCachingProcessor(Executor executor) {
        this.executor = executor;
    }

    public String[] getSupportedMethods() {
        return SUPPORTED_METHODS;
    }

    public Pattern getSupportedURLAsPattern() {
        return SM_FLV_PATTERN;
    }

    public String getSupportedURLAsString() {
        return null;
    }

    public Resource onRequest(HttpRequestHeader requestHeader)
            throws IOException {
        Matcher m = SM_FLV_PATTERN.matcher(requestHeader.getURI());
        if (!m.find()) {
            // it must not happen...
            return Resource.get(Resource.Type.URL, requestHeader.getURI());
        }

        MovieData data = new MovieData(m);

        if (data.getCache().exists()) {
            Logger.info("using cache: " + data.getCache().getCacheFileName());
            if (Boolean.getBoolean("touchCache")) {
                data.getCache().touch();
            }
            Resource r =
                    Resource.get(Resource.Type.URL,
                            data.getCache().getURLString());
            r.setResponseHeader(HttpHeader.CONTENT_TYPE, "video/flv");

            return r;
        }

        FutureTask<String> retrieveTitlteTask = null;
        if (Boolean.getBoolean("title")
                && (data.getIdInfo() == null || !data.getIdInfo().isTitleValid())) {
            retrieveTitlteTask =
                    new FutureTask<String>(new NicoCachingTitleRetriever(
                            data.getType(), data.getId()));
            executor.execute(retrieveTitlteTask);
        }

        Logger.info("no cache found: " + data.getCache().getCacheFileName());

        // [nl] ブラウザのレジューム無効と、NCでのレジューム対応
        requestHeader.removeMessageHeader("Range");
        requestHeader.removeMessageHeader("If-Range");
        long tmpSize = data.getCache().tmpLength();
        InputStream cacheInput = null;
        // [nl] 二重DLになる場合は、2つめのコネクションでは持っている分のキャッシュを利用する
        // また、2つめのコネクションは保存しない。かつ、強制的にレジュームする
        // TODO:排他処理がいるかもしれないが、まぁそこはそれ
        boolean dupconnect = Cache.getDLFlag(data.getCache().getId());

        // [nl] レジュームするよー
        if (tmpSize != 0
                && (dupconnect || Boolean.getBoolean("resumeDownload"))) {
            tmpSize = data.getCache().tmpLength();
            requestHeader.setMessageHeader("Range", "bytes=" + tmpSize + "-");
            cacheInput =
                    new BufferedInputStream(data.getCache().getTmpInputStream());
        }

        Resource r;
        try { // ensure cacheInput.close() in error cases.
            // [nl] DL中リストに入れる
            Cache.setDLFlag(data.getCache().getId(), Integer.MAX_VALUE);

            r = Resource.get(Resource.Type.URL, requestHeader.getURI());

            r.addTransferListener(new NicoCachingListener(data.getCache(),
                    retrieveTitlteTask, cacheInput, dupconnect));
        } catch (RuntimeException e) {
            Logger.error(e);
            CloseUtil.close(cacheInput);
            Cache.setDLFlag(data.getCache().getId(), -1);
            throw e;
        }

        return r;
    }

    /**
     * Class for manage various data for a movie.
     *
     */
    static class MovieData {
        private String format;
        private String id;
        private String suffix;
        private Entry idInfo;
        private String postfix;
        private String type;
        private Cache cache;

        MovieData(Matcher m) {
            initializeFormatIdSuffix(m);
            initializeIdInfo(getId());
            initializePostfix(format);
            initializeTypeCache(getIdInfo(), format);

            useAlternateCacheIfNecessary();
        }

        private void initializeFormatIdSuffix(Matcher m) {
            format = m.group(1);
            id = m.group(2);
            suffix = m.group(3);
        }

        private void initializeIdInfo(String id) {
            if (Boolean.getBoolean("title")) {
                idInfo = NicoIdInfoCache.getInstance().get(id);
            } else {
                idInfo = null;
            }
        }

        private void initializePostfix(String format) {
            if (format.equals("v")) {
                postfix = ".flv";
            } else if (format.equals("m")) {
                postfix = ".mp4";
            } else if (format.equals("s")) {
                postfix = ".swf";
            } else {
                postfix = ".unknown";
            }
        }

        private void initializeTypeCache(Entry idInfo, String format) {
            if (idInfo == null) {
                // this may not be correct, but we can not know without idInfo...
                if (format.endsWith("s")) {
                    type = "nm";
                } else {
                    type = "sm";
                }
                cache = new Cache(type + id, postfix);
            } else {
                type = idInfo.getType();
                cache = new Cache(type + id, postfix, idInfo.getTitle());
            }
        }

        private void useAlternateCacheIfNecessary() {
            if (!getCache().exists() && suffix != null) {
                if (idInfo == null) {
                    cache = new Cache(type + id + suffix, postfix);
                } else {
                    cache =
                            new Cache(type + id + suffix, postfix,
                                    idInfo.getTitle());
                }
            }
        }

        public Cache getCache() {
            return cache;
        }

        public Entry getIdInfo() {
            return idInfo;
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }
    }

}
