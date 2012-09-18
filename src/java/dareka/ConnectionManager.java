package dareka;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.CloseUtil;
import dareka.common.Config;
import dareka.common.HttpIOException;
import dareka.common.Logger;
import dareka.processor.HttpRequestHeader;
import dareka.processor.Processor;
import dareka.processor.Resource;

public class ConnectionManager extends Observable implements Runnable {
    private Socket browser;
    private Config config;
    private String processingURI;
    private volatile Resource processingResource;
    private volatile boolean stopped = false;

    private List<ProcessorEntry> processorEntries =
            new ArrayList<ProcessorEntry>();

    public ConnectionManager(Config config, Socket browser) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (browser == null) {
            throw new IllegalArgumentException("browser must not be null");
        }

        this.config = config;
        this.browser = browser;
    }

    public void run() {
        try {
            while (processAPairOfMessages()) {
                // loop until the method returns false.
                processingURI = null;
            }
            Logger.debugWithThread("loop end");
        } catch (ConnectException e) {
            // �A�E�g�o�E���h���ɐڑ����s
            Logger.debugWithThread(e);
            printWarning(e);
        } catch (SocketException e) {
            Logger.debugWithThread(e);

            // Connection reset �͂悭����̂Œʏ�̓��O�ɏo���Ȃ�
            if (!isConnectionReset(e)) {
                printWarning(e);
            }
        } catch (HttpIOException e) {
            Logger.debugWithThread(e);
        } catch (IOException e) {
            // NIO���g���Ă����Connection reset�ł͂Ȃ��ȉ��̃��b�Z�[�W��������
            // IOException�ɂȂ�B
            // �u�����̐ڑ��̓����[�g �z�X�g�ɋ����I�ɐؒf����܂����B�v
            // �u�m�����ꂽ�ڑ����z�X�g �R���s���[�^�̃\�E�g�E�F�A�ɂ���Ē��~����܂����B�v
            // UnknownHostException���ʏ탍�O�ɂ͏o���Ȃ�
            Logger.debugWithThread(e);
        } catch (CancelledKeyException e) {
            // read()���ɕʃX���b�h����stop()�œǂݍ��݂𒆒f�������
            // CancelledKeyException�ɂȂ�B
            // ������Java���s���̎����ˑ��B
            // �Ӑ}�����G���[�Ȃ̂Ń��O�ɂ͏o���Ȃ��B
            Logger.debugWithThread(e);
        } catch (ClosedSelectorException e) {
            // �ʃX���b�h����stop()��Selector��close()�����
            // ClosedSelectorException�ɂȂ�B
            // �Ӑ}�����G���[�Ȃ̂Ń��O�ɂ͏o���Ȃ��B
            Logger.debugWithThread(e);
        } catch (Exception e) {
            Logger.debugWithThread(e);
            printWarning(e);
        } finally {
            if (!browser.isClosed()) {
                consumeBrowserInput();
                CloseUtil.close(browser);
            }

            notifyCompletion();
        }
    }

    private void printWarning(Exception e) {
        Logger.warning("failed to process: " + processingURI + "\n\t"
                + e.toString());
    }

    private boolean isConnectionReset(SocketException e) {
        // ���̔�����@�ł������́c
        return e.getMessage().startsWith("Connection reset")
                || e.getMessage().startsWith("Software caused connection abort");
    }

    /**
     * �]���ȃf�[�^�𑗂��Ă���u���E�U�΍�B
     */
    private void consumeBrowserInput() {
        try {
            // java.net API�ŏ�������B
            // ���̂��߂ɔ�u���b�N���[�h�������B
            SocketChannel bc = browser.getChannel();
            // bc��null�ɂ͂Ȃ�Ȃ��B
            bc.configureBlocking(true);

            // ���̓ǂݍ��݂Ńf�b�h���b�N������邽�ߏo�͂͒�~�B
            // (FIN�𑗐M)
            browser.shutdownOutput();

            // IE��POST���N�G�X�g�̎��ɗ]����CRLF�𑗂��ė��Ă���̂�
            // IE�����M�𐳏�I���ł���悤�ɓǂݔ�΂��Ă��B
            // (FIN�̎�M�܂ő҂�)
            // �����ǂݔ�΂��Ă��Ȃ���Socket#close()�����
            // �u���b�Z�[�W�T�[�o�[�ɐڑ��ł��܂���ł����B�v�ȂǂɂȂ�
            // �Q�l: IE���]����CRLF�𑗐M���邱�Ƃɂ��ĐG����
            // �Ă�������̕���
            // http://support.microsoft.com/kb/823099/
            while (browser.getInputStream().read() != -1) {
                // no nothing
            }
        } catch (Exception e) {
            // IOException��Connection reset�n�̃G���[��CancelledKeyException��
            // ����B
            // ��������reset����Ă����ꍇ�͂����ł��܂���O�ɂȂ邪�A
            // ���ۂ�read()���Ă݂Ȃ��Ƌ�ʂ��t���Ȃ��̂Ŏd���Ȃ��B
            Logger.debugWithThread(e.toString() + "(consuming)");
        }
    }

    private boolean processAPairOfMessages() throws IOException {
        HttpRequestHeader requestHeader =
                new HttpRequestHeader(browser.getInputStream());
        processingURI = requestHeader.getURI();

        Logger.debugWithThread(requestHeader.getMethod() + " "
                + requestHeader.getURI());

        // [nl] �ݒ�t�@�C���̍X�V�`�F�b�N
        // processAPairOfMessages()�Ƃ��Ă̐U�����ł͂Ȃ���
        // new HttpRequestHeader�͓T�^�I�ȃu���b�N�ꏊ�Ȃ̂�
        // ���̒���ɏ������Ƃɂ���B
        if (config.reload()) {
            Logger.info("Reloading '" + config.getConfigFile().getName() + "'");
        }

        // �Ή�����Processor��T���ď���
        for (ProcessorEntry entry : processorEntries) {
            if (isMatchToEntry(entry, requestHeader)) {
                boolean canContinue =
                        useProcessor(requestHeader, entry.getProcessor());
                Logger.debugWithThread("end");
                return canContinue;
            }
        }

        throw new HttpIOException("request cannot be processed:\r\n"
                + requestHeader);
    }

    private boolean useProcessor(HttpRequestHeader requestHeader,
            Processor processor) throws IOException {
        processingResource = processor.onRequest(requestHeader);

        if (stopped) {
            // ���̒�~�v���`�F�b�N��processingResource�擾���
            // ��ɖ�����΂Ȃ�Ȃ�
            return false;
        }

        if (processingResource == null) {
            throw new HttpIOException(
                    "request processor failed to handle request:\r\n"
                            + requestHeader);
        }

        try { // ensure (processingResource == null) after the transfer.
            requestHeader.removeHopByHopHeaders();
            return processingResource.transferTo(browser, requestHeader, config);
        } finally {
            processingResource = null;
        }
    }

    private boolean isMatchToEntry(ProcessorEntry entry,
            HttpRequestHeader requestHeader) {
        if (entry.getMethod() != null) {
            if (!entry.getMethod().equals(requestHeader.getMethod())) {
                return false;
            }
        }

        if (entry.getUri() != null) {
            Matcher m = entry.getUri().matcher(requestHeader.getURI());
            if (!m.lookingAt()) {
                return false;
            }
            // TODO �}�b�`���ʂ�Processor�ōė��p�ł���悤�ɂ��č�����
        }

        return true;
    }

    private void notifyCompletion() {
        setChanged();
        notifyObservers();
    }

    public void addProcessor(String method, Pattern url, Processor processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor must not be null");
        }

        ProcessorEntry entry = new ProcessorEntry(method, url, processor);

        processorEntries.add(entry);
    }

    /**
     * stop blocking operation.
     */
    public void stop() {
        stopped = true;

        CloseUtil.close(browser);

        try {
            processingResource.stopTransfer();
        } catch (NullPointerException npe) {
            // processingResource�̏��������͕ʃX���b�h�ōs����̂�
            // null�`�F�b�N����Ȃ�Ăяo�����A�g�~�b�N�ɍs��Ȃ���΂Ȃ�Ȃ���
            // ���ꂾ�ƃ��b�N�Ǘ������G�ɂȂ�̂�optimistic�ɍs��
            // null�������ꍇ�͊��ɓ]���I�����Ă���̂�OK
            Logger.debugWithThread(npe);
        }
    }
}
