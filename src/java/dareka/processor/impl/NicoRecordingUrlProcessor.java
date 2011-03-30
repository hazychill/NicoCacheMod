package dareka.processor.impl;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.Logger;
import dareka.processor.HttpHeader;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;

/**
 * Record a pair of type(sm/ax/ca, etc.) and id of a movie.
 * The URL to the movie server does not contain the type.
 * Additionally, in some cases, the browser does not show
 * the movie via the watch page.
 */
public class NicoRecordingUrlProcessor implements Processor {
    private static final String[] SUPPORTED_METHODS =
            new String[] { HttpHeader.GET, HttpHeader.HEAD };

    /*
     *  from blog embedded player:
     *  http://ext.nicovideo.jp/thumb_watch/sm0000
     *  others:
     *  http://ext.nicovideo.jp/thumb/sm0000
     *  http://www.nicovideo.jp/api/getflv/sm0000?
     *  http://www.nicovideo.jp/api/getflv?v=sm0000  (obsoleted?)
     */
    private static final Pattern TYPE_ID_URL_PATTERN =
            Pattern.compile("^http://(?:ext\\.nicovideo\\.jp/thumb(?:_watch)?|www\\.nicovideo\\.jp/api/getflv)/([a-z]{2})(\\d+)");

    public String[] getSupportedMethods() {
        return SUPPORTED_METHODS;
    }

    public Pattern getSupportedURLAsPattern() {
        return TYPE_ID_URL_PATTERN;
    }

    public String getSupportedURLAsString() {
        return null;
    }

    public Resource onRequest(HttpRequestHeader requestHeader)
            throws IOException {
        String url = requestHeader.getURI();

        Matcher m = TYPE_ID_URL_PATTERN.matcher(url);
        if (!m.find()) {
            // internal error
            throw new IllegalStateException("unexpected url: " + url);
        }

        String type = m.group(1);
        String id = m.group(2);

        NicoIdInfoCache.getInstance().putOnlyTypeAndId(type, id);
        Logger.debugWithThread("type recorded: " + id + " => " + type);

        Resource r = Resource.get(Resource.Type.URL, url);
        return r;
    }
}
