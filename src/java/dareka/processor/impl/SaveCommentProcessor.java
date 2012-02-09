package dareka.processor.impl;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;
import dareka.processor.TransferListener;

public class SaveCommentProcessor implements Processor {

	private String[] POST = new String[] { "POST" };
	private Pattern URL_PATTERN = Pattern
			.compile("^http://msg.nicovideo.jp/[0-9]+/api/$");

	@Override
	public String[] getSupportedMethods() {
		return POST;
	}

	@Override
	public Pattern getSupportedURLAsPattern() {
		return URL_PATTERN;
	}

	@Override
	public String getSupportedURLAsString() {
		return null;
	}

	@Override
	public Resource onRequest(HttpRequestHeader requestHeader)
			throws IOException {
		Resource r = Resource.get(Resource.Type.URL, requestHeader.getURI());
		String id = createId();
		TransferListener listener = new SaveCommentListener(id);
		r.addTransferListener(listener);

		return r;
	}

	private String createId() {
		// TODO
		return UUID.randomUUID().toString();
	}

}
