package dareka.processor.impl;

import java.io.IOException;
import java.util.regex.Pattern;

import dareka.processor.HttpHeader;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;

public class GetPostProcessor implements Processor {
    private static final String[] SUPPORTED_METHODS =
            new String[] { HttpHeader.GET, HttpHeader.POST, HttpHeader.HEAD };

    public String[] getSupportedMethods() {
        return SUPPORTED_METHODS;
    }

    public Pattern getSupportedURLAsPattern() {
        return null;
    }

    public String getSupportedURLAsString() {
        return null;
    }

    public Resource onRequest(HttpRequestHeader requestHeader)
            throws IOException {
        return Resource.get(Resource.Type.URL, requestHeader.getURI());
    }
}
