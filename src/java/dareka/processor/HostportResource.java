package dareka.processor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Config;
import dareka.common.HttpIOException;

/**
 * Resource which is retrieved from a TCP/IP connection.
 *
 * <p>
 * This class cannot handle onTransferXXX events.
 *
 */
public class HostportResource extends Resource {
    private static final Pattern HOSTPORT_PATTERN =
        Pattern.compile("^([^:]+):(\\d+)$");

    private InetSocketAddress host;

    private volatile Selector processingSelector;

    public HostportResource(String resource) throws HttpIOException {
        Matcher m = HOSTPORT_PATTERN.matcher(resource);
        if (!m.find()) {
            throw new HttpIOException("invalid hostport: " + resource);
        }

        host = new InetSocketAddress(m.group(1), Integer.parseInt(m.group(2)));
    }

    @Override
    public boolean transferTo(Socket receiver, HttpRequestHeader requestHeader,
            Config config) throws IOException {
        // Socket#isClosed()
        // Socket#isInputShutdown()
        // Socket#isOutputShutdown()
        // �̂�����ł��I�������m�ł��Ȃ��̂�
        // ����̂��߂ɔ�u���b�NI/O���g���Ď�������
        SocketChannel sc = getServerChannelForConnect();

        try {
            handleConnectOnChannel(sc, receiver.getChannel());
        } finally {
            CloseUtil.close(sc);
        }

        return false;
    }

    @Override
    public void stopTransfer() {
        super.stopTransfer();
        CloseUtil.close(processingSelector);
    }

    /* (�� Javadoc)
     * @see dareka.processor.Resource#doSetMandatoryHeader(dareka.processor.HttpResponseHeader)
     */
    @Override
    protected void doSetMandatoryResponseHeader(
            HttpResponseHeader responseHeader) {
        responseHeader.setMessageHeader(HttpHeader.CONNECTION,
                HttpHeader.CONNECTION_CLOSE);
    }

    /**
     * �ڑ�����A�E�g�o�E���h����Channel���擾����B
     * �ݒ�ɂ����Proxy���o�R����B
     *
     * @return �ڑ������A�E�g�o�E���h���̃`���l���B
     * @throws IOException
     */
    private SocketChannel getServerChannelForConnect() throws IOException {
        SocketChannel sc = SocketChannel.open();
        try { // ensure sc.close() in case of error.
            String proxyHost = System.getProperty("proxyHost");
            int proxyPort = Integer.getInteger("proxyPort").intValue();

            // [nl] SSL�Z�J���_���v���L�V�̑I��
            if (!Boolean.getBoolean("proxySSL") || proxyHost.equals("")) {
                sc.connect(host);
            } else {
                Socket proxy = sc.socket();
                proxy.connect(new InetSocketAddress(proxyHost, proxyPort));

                HttpRequestHeader requestHeader =
                    new HttpRequestHeader("CONNECT " + host.getHostName() + ":"
                            + host.getPort() + " HTTP/1.1\r\n\r\n");
                requestHeader.setMessageHeader(HttpHeader.CONNECTION,
                        HttpHeader.CONNECTION_CLOSE);
                HttpUtil.sendHeader(proxy, requestHeader);

                HttpResponseHeader responseHeader =
                    new HttpResponseHeader(proxy.getInputStream());

                if (responseHeader.getStatusCode() != 200) {
                    throw new HttpIOException("failed to connect: "
                            + responseHeader.toString());
                }
            }
        } catch (IOException e) {
            CloseUtil.close(sc);
            throw e;
        } catch (RuntimeException e) {
            CloseUtil.close(sc);
            throw e;
        }

        return sc;
    }

    /**
     * CONNECT�����BChannel��close�����₷�����邽�߂ɕ����B
     *
     * @param sc
     * @param bc
     * @throws IOException
     */
    private void handleConnectOnChannel(SocketChannel sc, SocketChannel bc)
    throws IOException {
        HttpResponseHeader responseHeader =
            new HttpResponseHeader(
            "HTTP/1.1 200 Connection established\r\n\r\n");

        execSendingHeaderSequence(bc.socket().getOutputStream(), responseHeader);

        sc.configureBlocking(false);
        bc.configureBlocking(false);

        processingSelector = Selector.open();
        try {
            if (isStopped()) {
                // ����ȍ~��processingSelector��open()���Ă͂����Ȃ��B
                return;
            }

            SelectionKey scKey =
                sc.register(processingSelector, SelectionKey.OP_READ);
            SelectionKey bcKey =
                bc.register(processingSelector, SelectionKey.OP_READ);
            scKey.attach(bc); // ��ő΂ɂȂ鑤���擾�ł���悤�ɂ��Ă���
            bcKey.attach(sc);

            handleConnectOnSelector(processingSelector);
        } finally {
            CloseUtil.close(processingSelector);
            processingSelector = null;
        }
    }

    /**
     * CONNECT�����BSelector��close�����₷�����邽�߂ɕ����B
     *
     * @param sel
     * @throws IOException
     */
    private void handleConnectOnSelector(Selector sel) throws IOException {
        ByteBuffer bbuf = ByteBuffer.allocate(BUF_SIZE);
        int len;

        // �L�[�Z�b�g��0�̏�Ԃ�select()���ĂԂƉi���ɑ҂��Ă��܂��B
        // �������������ꂽ�L�[�Z�b�g�͑I�𑀍���s��Ȃ��Ǝ��ۂɍ폜����Ȃ��B
        // �����ōX�V�����邽�߂�����selectNow()���ĂԂ�
        // ���������select()�̖߂�l��0�ɂȂ邱�Ƃ�����B
        int selcount;
        while ((selcount = sel.selectNow()) >= 0 && sel.keys().size() > 0
                && (selcount > 0 || sel.select() >= 0)) {
            Set<SelectionKey> selKeys = sel.selectedKeys();

            // for (SelectionKey key : selKeys)������ƂȂ���
            // ConcurrentModificationException�ɂȂ邱�Ƃ�����
            for (Iterator<SelectionKey> ite = selKeys.iterator(); ite.hasNext();) {
                SelectionKey key = ite.next();
                ite.remove();

                SocketChannel readCh = (SocketChannel) key.channel();
                SocketChannel writeCh = (SocketChannel) key.attachment();

                bbuf.clear();
                try {
                    len = readCh.read(bbuf);
                } catch (IOException e) {
                    // select()���g���Ă���̂�
                    // �u�����̐ڑ��̓����[�g �z�X�g�ɋ����I�ɐؒf����܂����B�v
                    // �Őؒf����邱�Ƃ͂Ȃ������m��Ȃ���
                    // �O�̂��ߑΉ����Ă���
                    len = -1;
                }

                if (len == -1) {
                    key.cancel();
                    writeCh.socket().shutdownOutput();
                } else {
                    bbuf.flip();
                    while (bbuf.hasRemaining()) {
                        writeCh.write(bbuf);
                    }
                }
            }
        }
    }

}
