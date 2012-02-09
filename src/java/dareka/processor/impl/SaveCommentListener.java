package dareka.processor.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import dareka.common.Logger;
import dareka.processor.HttpResponseHeader;
import dareka.processor.TransferListener;

public class SaveCommentListener implements TransferListener {

	private String id;
	private OutputStream receiverOut;

	public SaveCommentListener(String id) {
		this.id = id;
	}

	@Override
	public void onResponseHeader(HttpResponseHeader responseHeader) {
		Logger.info("SaveCommentListener.onResponseHeader " + id);
	}

	@Override
	public void onTransferBegin(OutputStream receiverOut) {
		Logger.info("SaveCommentListener.onTransferBegin " + id);
		this.receiverOut = receiverOut;
	}

	@Override
	public void onTransferring(byte[] buf, int length) {
		// TODO Auto-generated method stub
		Logger.info("SaveCommentListener.onTransferring " + id);
		try {
			receiverOut.write(buf, 0, length);
		} catch (IOException e) {
			Logger.info(e.getMessage());
			try {
				Writer sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				pw.flush();
				sw.flush();
				Logger.debug(sw.toString());
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	@Override
	public void onTransferEnd(boolean completed) {
		Logger.info("SaveCommentListener.onTransferEnd " + id);
	}

}
