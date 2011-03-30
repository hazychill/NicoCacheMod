package dareka.processor.impl;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.processor.HttpHeader;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;

/**
 * Record type(sm/ax/ca, etc.), title of movies. The URL to the movie server
 * does not contain the type, so it is mandatory to record the type when
 * we see the watch page.
 *
 */
public class NicoRecordingWatchProcessor implements Processor {
    private static final String[] SUPPORTED_METHODS =
            new String[] { HttpHeader.GET };

    private static final Pattern SM_WATCH_PAGE_PATTERN =
            Pattern.compile("^http://www\\.nicovideo\\.jp/watch/([a-z]{2})?(\\d+)(?:\\?.*)?$");

    public String[] getSupportedMethods() {
        return SUPPORTED_METHODS;
    }

    public Pattern getSupportedURLAsPattern() {
        return SM_WATCH_PAGE_PATTERN;
    }

    public String getSupportedURLAsString() {
        return null;
    }

    public Resource onRequest(HttpRequestHeader requestHeader)
            throws IOException {
        String url = requestHeader.getURI();

        Matcher m = SM_WATCH_PAGE_PATTERN.matcher(url);
        if (!m.find()) {
            // internal error
            throw new IllegalStateException("unexpected url: " + url);
        }

        String type = m.group(1);
        String id = m.group(2);

        Resource r = Resource.get(Resource.Type.URL, url);
        NicoRecordingWatchListener l = new NicoRecordingWatchListener(type, id);
        r.addTransferListener(l);

        return r;
    }
}
