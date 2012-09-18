package dareka.processor;

import java.io.OutputStream;

/**
 * �f�[�^�]�����ɃV�X�e������Ăяo�����C�x���g���X�i�B
 * {@link Resource#addTransferListener(TransferListener)}�ɓo�^���Ďg���B
 *
 */
public interface TransferListener {
    /**
     * �A�E�g�o�E���h�����烊�N�G�X�g�w�b�_������������V�X�e�����Ăяo���B
     *
     * @param responseHeader
     */
    void onResponseHeader(HttpResponseHeader responseHeader);

    /**
     * [nl] �f�[�^�̓]�����n�߂�O�ɌĂяo���B
     *
     * @param receiverOut
     */
    void onTransferBegin(OutputStream receiverOut);

    /**
     * �A�E�g�o�E���h������C���o�E���h���Ƀf�[�^��]�����邽�т�
     * �V�X�e�����Ăяo���B
     *
     * @param buf �]������f�[�^�B
     * @param length buf�̒��̗L���ȃf�[�^�̒����B
     */
    void onTransferring(byte[] buf, int length);

    /**
     * �f�[�^�̓]�����I��������V�X�e�����Ăяo���B
     *
     * @param completed �Ō�܂œ]���ł�����true�B�r���Œ��f�����ꍇ�A
     * �������͎��ۂɓ]�����ꂽ�f�[�^�ʂ�Content-Length�ƈقȂ����ꍇ��false�B
     */
    void onTransferEnd(boolean completed);

}
