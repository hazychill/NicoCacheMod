package dareka.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import dareka.common.Logger;

public class HttpUtil {
    private static final int BUF_SIZE = 32 * 1024;

    private HttpUtil() {
        // avoid instantiation
    }

    public static void sendHeader(Socket receiver, HttpHeader header)
            throws IOException {
        sendHeader(receiver.getOutputStream(), header);
    }

    public static void sendHeader(OutputStream receiverOut, HttpHeader header)
            throws IOException {
        byte[] headerBytes = header.getBytes();
        receiverOut.write(headerBytes);
    }

    public static void sendBody(Socket receiver, Socket sender,
            long contentLength) throws IOException {
        SocketChannel senderCh = sender.getChannel();
        SocketChannel receiverCh = receiver.getChannel();

        sendBodyOnChannel(receiverCh, senderCh, contentLength);
    }

    public static void sendBody(OutputStream out, InputStream in,
            long contentLength) throws IOException {
        sendBodyOnChannel(Channels.newChannel(out), Channels.newChannel(in),
                contentLength);

    }

    private static void sendBodyOnChannel(WritableByteChannel receiverCh,
            ReadableByteChannel senderCh, long contentLength)
            throws IOException {
        long maxLength = contentLength == -1 ? Long.MAX_VALUE : contentLength;

        ByteBuffer bbuf = ByteBuffer.allocate(BUF_SIZE);
        int len = 0;
        for (long currentLength = 0; currentLength < maxLength; currentLength +=
                len) {
            bbuf.clear();
            long remain = maxLength - currentLength;
            if (remain < bbuf.limit()) {
                bbuf.limit((int) remain);
            }

            len = senderCh.read(bbuf);
            if (len == -1) {
                break;
            }

            bbuf.flip();
            receiverCh.write(bbuf);
        }

        if (contentLength != -1 && len == -1) {
            Logger.warning("content may be imcomplete.");
        }
    }

    public static InputStream getDecodedInputStream(byte[] content,
            String contentEncoding) throws IOException {
        InputStream in = new ByteArrayInputStream(content);

        if (HttpHeader.CONTENT_ENCODING_GZIP.equalsIgnoreCase(contentEncoding)) {
            return new GZIPInputStream(in);
        } else if (HttpHeader.CONTENT_ENCODING_DEFLATE.equalsIgnoreCase(contentEncoding)) {
            return new InflaterInputStream(in);
        } else if (HttpHeader.IDENTITY.equalsIgnoreCase(contentEncoding)) {
            // [nl] �{��Content-Encoding��identity�͎g�p���ׂ��ł͂Ȃ���
            // ����ł��g�p�����ꍇ�͒P�ɖ�������
            // see RFC 2616 3.5 Content Codings
            return in;
        }

        if (contentEncoding != null) {
            Logger.warning("unknown Content-Encoding: " + contentEncoding);
        }

        return in;
    }
}
