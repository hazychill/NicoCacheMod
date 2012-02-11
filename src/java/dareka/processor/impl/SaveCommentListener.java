package dareka.processor.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import dareka.common.Logger;
import dareka.processor.HttpResponseHeader;
import dareka.processor.TransferListener;

public class SaveCommentListener implements TransferListener {

	private String id;
	private ByteArrayOutputStream bufferStream;
	private Pattern viewCounterPattern;

	private boolean runOutOfBuffer;
	private OutputStream fileOutput;
	private boolean isFirstFileOut;
	private boolean isGzipDeflated;
	private boolean isNotDeflated;
	private File outputFile;

	public SaveCommentListener(String id) {
		this.id = id;
		bufferStream = new ByteArrayOutputStream();
		// <view_counter [^>]*id="([a-z][a-z][0-9]+)"
		viewCounterPattern = Pattern
				.compile("<view_counter [^>]*id=\"([a-z][a-z][0-9]+)\"");
		runOutOfBuffer = false;
		fileOutput = null;
		isFirstFileOut = true;
		isGzipDeflated = false;
		isNotDeflated = false;
	}

	@Override
	public void onResponseHeader(HttpResponseHeader responseHeader) {
		String contentEncoding = responseHeader
				.getMessageHeader("Content-Encoding");
		if (contentEncoding == null || contentEncoding.length() == 0) {
			isNotDeflated = true;
		} else if ("identity".equalsIgnoreCase(contentEncoding)) {
			isNotDeflated = true;
		} else if ("gzip".equalsIgnoreCase(contentEncoding)) {
			isGzipDeflated = true;
		}
	}

	@Override
	public void onTransferBegin(OutputStream receiverOut) {
		// Nothing to do...
	}

	@Override
	public void onTransferring(byte[] buf, int length) {
		try {
			if (isNotDeflated == false && isGzipDeflated == false) {
				// Only raw or gzip deflated data is supprted
				return;
			}

			if (fileOutput == null && runOutOfBuffer == false) {
				try {
					int maxBufferLength = getMaxBufferLength();

					bufferStream.write(buf, 0, length);
					bufferStream.flush();

					String charset = getCharset();
					String str;
					if (isNotDeflated) {
						str = Charset
								.forName(charset)
								.decode(ByteBuffer.wrap(bufferStream
										.toByteArray())).toString();
					} else if (isGzipDeflated) {
						str = inflateAndDecode(bufferStream, charset);
					} else {
						str = null;
						Logger.info("Contract violation");
						return;
					}

					if (str != null && str.indexOf("<chat thread=") != -1) {
						Matcher m = viewCounterPattern.matcher(str);
						if (m.find()) {
							String videoId = m.group(1);
							String numDir;
							if (videoId.length() >= 4) {
								numDir = videoId
										.substring(videoId.length() - 2);
							} else if (videoId.length() == 3) {
								numDir = "0" + videoId.substring(2);
							} else {
								numDir = null;
							}
							File outputDir = getOutputDirectory();
							if (numDir != null) {
								outputDir = new File(outputDir, numDir);
							}
							boolean dirExists = true;
							if (!outputDir.isDirectory()) {
								dirExists = outputDir.mkdirs();
							}
							if (dirExists) {
								String fileName = MessageFormat.format(
										"_{0}-{1}.xml{2}", videoId, id,
										(isGzipDeflated) ? (".gz") : (""));
								outputFile = new File(outputDir, fileName);
								fileOutput = new FileOutputStream(outputFile);
							}
						}
					}

					if (bufferStream.size() > maxBufferLength) {
						runOutOfBuffer = true;
					}
				} catch (UnsupportedEncodingException e) {
					logException(e);
				} catch (IOException e) {
					logException(e);
				}

			}

			if (fileOutput != null) {
				if (isFirstFileOut) {
					isFirstFileOut = false;
					bufferStream.flush();
					byte[] buffered = bufferStream.toByteArray();
					fileOutput.write(buffered, 0, buffered.length);
				}
				fileOutput.write(buf, 0, length);
			}
		} catch (Throwable e) {
			logException(e);
		}
	}

	private String inflateAndDecode(ByteArrayOutputStream bufferStream,
			String charset) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			byte[] array = bufferStream.toByteArray();
			ByteArrayInputStream bais = new ByteArrayInputStream(array);
			GZIPInputStream gzis = new GZIPInputStream(bais);

			int b;
			while ((b = gzis.read()) != -1) {
				baos.write(b);
			}
		} catch (IOException e) {
			// ignore `Unexpected end of GZIP stream' error
		}

		baos.flush();
		Charset c = Charset.forName(charset);
		ByteBuffer bb = ByteBuffer.wrap(baos.toByteArray());
		return c.decode(bb).toString();
	}

	@Override
	public void onTransferEnd(boolean completed) {
		try {
			if (fileOutput != null) {
				fileOutput.flush();
				fileOutput.close();
			}

			if (outputFile != null) {
				String tempFileName = outputFile.getName();
				if (tempFileName.startsWith("_")) {
					File dstFile;
					String fileName = tempFileName.substring(1);
					File dir = outputFile.getParentFile();
					if (tempFileName.endsWith(".gz")) {
						fileName = fileName.substring(0, fileName.length() - 3);
						dstFile = new File(dir, fileName);
						copyAndInflateFile(outputFile, dstFile);
					} else {
						dstFile = new File(dir, fileName);
						copyFile(outputFile, dstFile);
					}
					outputFile.delete();
					Logger.info("Comment saved: " + dstFile.getAbsolutePath());
				}

			}
		} catch (IOException e) {
			logException(e);
		}
	}

	private void copyFile(File srcFile, File dstFile) throws IOException {
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(srcFile);
			output = new FileOutputStream(dstFile);
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer, 0, buffer.length)) != -1) {
				output.write(buffer, 0, count);
			}
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Throwable e) {
					logException(e);
				}
			}
			if (output != null) {
				try {
					output.close();
				} catch (Throwable e) {
					logException(e);
				}
			}
		}
	}

	private void copyAndInflateFile(File srcFile, File dstFile)
			throws IOException {
		InputStream input = null;
		InputStream gzis = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(srcFile);
			gzis = new GZIPInputStream(input);
			output = new FileOutputStream(dstFile);
			byte[] buffer = new byte[8192];
			int count;
			while ((count = gzis.read(buffer, 0, buffer.length)) != -1) {
				output.write(buffer, 0, count);
			}
		} finally {
			if (gzis != null) {
				try {
					gzis.close();
				} catch (Throwable e) {
					logException(e);
				}
			}
			if (input != null) {
				try {
					input.close();
				} catch (Throwable e) {
					logException(e);
				}
			}
			if (output != null) {
				try {
					output.close();
				} catch (Throwable e) {
					logException(e);
				}
			}
		}
	}

	private void logException(Throwable e) {
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

	private int getMaxBufferLength() {
		Integer maxBufferLengthObj = Integer
				.getInteger("commentDetectBufferLength");
		int maxBufferLength;
		if (maxBufferLengthObj != null) {
			maxBufferLength = maxBufferLengthObj.intValue();
		} else {
			maxBufferLength = 8192;
		}
		return maxBufferLength;
	}

	private String getCharset() {
		String commentXmlCharset = System.getProperty("commentXmlCharset");
		if (commentXmlCharset == null) {
			commentXmlCharset = "UTF-8";
		}
		return commentXmlCharset;
	}

	private File getOutputDirectory() {
		String outputDirPath = System.getProperty("commentOutputDirectory");
		File outputDir;
		if (outputDirPath != null) {
			outputDir = new File(outputDirPath);
		} else {
			File cacheDir = new File("cache");
			outputDir = new File(cacheDir, "xml");
		}
		return outputDir;
	}
}
