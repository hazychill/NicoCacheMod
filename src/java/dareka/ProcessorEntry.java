package dareka;

import java.util.regex.Pattern;

import dareka.processor.Processor;

public class ProcessorEntry {
    private String method;
    private Pattern uri;
    private Processor processor;

    public ProcessorEntry(String method, Pattern url, Processor processor) {
        this.method = method;
        this.uri = url;
        this.processor = processor;
    }

    public String getMethod() {
        return method;
    }

    public Pattern getUri() {
        return uri;
    }

    public Processor getProcessor() {
        return processor;
    }

}
