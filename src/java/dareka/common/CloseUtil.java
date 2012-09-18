package dareka.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;

/**
 * ���\�[�X��close()���Ăԃ��[�e�B���e�B�N���X�B
 * ���\�[�X���[�N������邽�߁Aclose()���K�v�ȃ��\�[�X���m�ۂ�����
 * �K��try�߂̒��Ŏg�p���Afinally�߂�close()����K�v������B
 * colse()��IOException��throw����̂ŁAfinally�߂̒��ł�
 * try�߂��g���K�v������R�[�f�B���O���璷�ɂȂ�̂ŁA
 * ���������̃N���X�ɂ܂Ƃ߂�B
 * 
 */
public class CloseUtil {
    private CloseUtil() {
        // avoid instantiation.
    }

    /**
     * ���\�[�X���O�̔����Ȃ��ɕ���B
     * 
     * @param resource ���郊�\�[�X�Bnull�̏ꍇ�͉��������ɐ��������B
     * @return ����������true�B���炩�̃G���[������������false�B
     */
    public static boolean close(Closeable resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            Logger.debugWithThread(e);
            return false;
        }

        return true;
    }

    /**
     * ���\�[�X���O�̔����Ȃ��ɕ���B
     * Selector��Closeable���������Ȃ��̂ŃI�[�o�[���[�h�őΉ��B
     * 
     * @param resource ���郊�\�[�X�Bnull�̏ꍇ�͉��������ɐ��������B
     * @return ����������true�B���炩�̃G���[������������false�B
     */
    public static boolean close(Selector resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            Logger.debugWithThread(e);
            return false;
        }

        return true;
    }

    /**
     * ���\�[�X���O�̔����Ȃ��ɕ���B
     * Socket��Java SE 7�܂�Closeable���������Ȃ��̂�
     * �����_�ł̓I�[�o�[���[�h�őΉ��B
     * 
     * @param resource ���郊�\�[�X�Bnull�̏ꍇ�͉��������ɐ��������B
     * @return ����������true�B���炩�̃G���[������������false�B
     */
    public static boolean close(Socket resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            Logger.debugWithThread(e);
            return false;
        }

        return true;
    }

    /**
     * ���\�[�X���O�̔����Ȃ��ɕ���B
     * ServerSocket��Java SE 7�܂�Closeable���������Ȃ��̂�
     * �����_�ł̓I�[�o�[���[�h�őΉ��B
     * 
     * @param resource ���郊�\�[�X�Bnull�̏ꍇ�͉��������ɐ��������B
     * @return ����������true�B���炩�̃G���[������������false�B
     */
    public static boolean close(ServerSocket resource) {
        if (resource == null) {
            return true;
        }

        try {
            resource.close();
        } catch (IOException e) {
            Logger.debugWithThread(e);
            return false;
        }

        return true;
    }

}
