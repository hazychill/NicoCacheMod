package dareka.processor;

import java.io.IOException;
import java.util.regex.Pattern;

public interface Processor {
    /**
     * �T�|�[�g���Ă��郁�\�b�h��₢���킹�鎞�ɃV�X�e�����Ăяo���܂��B
     * GET��POST�ȂǁA�T�|�[�g���Ă��郁�\�b�h��z��ŕԂ��Ă��������B
     * 
     * @return �T�|�[�g���Ă��郁�\�b�h��񋓂����z��B
     */
    String[] getSupportedMethods();
    
    /**
     * �T�|�[�g���Ă���URL��₢���킹�鎞�ɃV�X�e�����Ăяo���܂��B
     * �T�|�[�g���Ă���URL�Ƀ}�b�`���鐳�K�\����Ԃ��Ă��������B
     * 
     * @return �T�|�[�g���Ă���URL�Ƀ}�b�`���鐳�K�\���B
     * null��Ԃ����ꍇ�͑����getSupportedURLAsString()���Ăяo�����B
     */
    Pattern getSupportedURLAsPattern();
    
    /**
     * �T�|�[�g���Ă���URL��₢���킹�鎞�ɃV�X�e�����Ăяo���܂��B
     * �T�|�[�g���Ă���URL��߂�l�Ŏ����Ă��������B
     * �擪���߂�l�̕�����Ŏn�܂��Ă���URL�Ƀ}�b�`���܂��B
     * ���K�\�����s�v�ȊȈՔŁB
     * 
     * @return �T�|�[�g���Ă���URL�B
     * getSupportedURLAsPattern()�ł������ł�null��Ԃ����ꍇ�͑S�Ă�URL�Ƀ}�b�`�B
     */
    String getSupportedURLAsString();
    
    /**
     * �T�|�[�g���Ă��郁�\�b�h��URL�ɍ��v�������N�G�X�g��
     * �C���o�E���h(�u���E�U)�����瓞����������
     * �V�X�e�����Ăяo���܂��B
     * ���X�|���X���ǂ̃��\�[�X����擾���邩�����肵�A
     * �߂�l�Ŏ����Ă��������B
     * 
     * @param requestHeader �����������N�G�X�g�̃w�b�_�B
     * ������C������ƁA�A�E�g�o�E���h(�T�[�o)���ɑ��M����w�b�_��
     * ���f�����B
     * @return ���X�|���X�Ƃ��ĕԂ����\�[�X�B
     * @throws IOException 
     */
    Resource onRequest(HttpRequestHeader requestHeader) throws IOException;
}
