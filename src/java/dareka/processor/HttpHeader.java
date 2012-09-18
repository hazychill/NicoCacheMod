package dareka.processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.HttpIOException;
import dareka.common.Logger;

/**
 * HTTP-message�̒��ۉ��B
 *
 */
public class HttpHeader {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String HEAD = "HEAD";

    public static final String CONNECTION = "Connection";
    public static final String CONNECTION_CLOSE = "close";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String CONTENT_ENCODING_DEFLATE = "deflate";
    public static final String CONTENT_ENCODING_GZIP = "gzip";
    public static final String IDENTITY = "identity";

    // [nl] ���t�������w�b�_
    public static final String DATE = "Date";
    public static final String EXPIRES = "Expires";
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    public static final String LAST_MODIFIED = "Last-Modified";

    /**
     * Message Headers.
     *
     * from RFC2616 4.2 Message Headers:
     *
     * <pre>
     *        message-header = field-name &quot;:&quot; [ field-value ]
     *        field-name     = token
     *        field-value    = *( field-content | LWS )
     *        field-content  = &lt;the OCTETs making up the field-value
     *                         and consisting of either *TEXT or combinations
     *                         of token, separators, and quoted-string&gt;
     * </pre>
     *
     *
     * 2.2 Basic Rules:
     * <pre>
     *        OCTET          = &lt;any 8-bit sequence of data&gt;
     *        CHAR           = &lt;any US-ASCII character (octets 0 - 127)&gt;
     *        TEXT           = &lt;any OCTET except CTLs,
     *                         but including LWS&gt;
     *        CTL            = &lt;any US-ASCII control character
     *                         (octets 0 - 31) and DEL (127)&gt;
     *        LWS            = [CRLF] 1*( SP | HT )
     *        token          = 1*&lt;any CHAR except CTLs or separators&gt;
     *        separators     = &quot;(&quot; | &quot;)&quot; | &quot;&lt;&quot; | &quot;&gt;&quot; | &quot;@&quot;
     *                       | &quot;,&quot; | &quot;;&quot; | &quot;:&quot; | &quot;\&quot; | &lt;&quot;&gt;
     *                       | &quot;/&quot; | &quot;[&quot; | &quot;]&quot; | &quot;?&quot; | &quot;=&quot;
     *                       | &quot;{&quot; | &quot;}&quot; | SP | HT
     *        quoted-string  = ( &lt;&quot;&gt; *(qdtext | quoted-pair ) &lt;&quot;&gt; )
     *        qdtext         = &lt;any TEXT except &lt;&quot;&gt;&gt;
     *        quoted-pair    = &quot;\&quot; CHAR
     * </pre>
     */
    private static final Pattern MESSAGE_HEADER_PATTERN =
            Pattern.compile("^([^:]+):\\s*(.*)\r?\n");
    /*
     * �����G���R�[�f�B���O�̈����ɂ��ă���:
     *
     * field-content�͓��{��Ȃǂ̔�ASCII��������蓾��B����ɂǂ��Ή����邩�B
     *
     * HTTP�̂قƂ�ǂ�field-content��ASCII�����������Ȃ��̂�
     * ISO-8859-1, MS932, UTF-8����v�ȕ����R�[�h�̂ǂ���g���Ă����͓����B
     * �������A�ꕔ�̃u���E�U�͔�ASCII�������܂߂�URL�𑗂��Ă���B
     * (Request-Line��Referer�t�B�[���h�ȂǁB)
     * �����RFC3986�ᔽ�Ȃ̂����A�����͂����Ă��Ή�����K�v������B
     * �܂��A��W���w�b�_����Content-Disposition����ASCII�������܂ݓ���B
     *
     * ����ANicoCache�̓����ł� URLConnection#addRequestProperty(String, String)
     * ���g���Ă��邱�Ƃ◘�֐��̂��߂�field-content��String�Ƃ��Ĉ����Ă���B
     * ���̂��߁Afield-content�̃o�C�g�V�[�P���X��String�̃}�b�s���O��
     * �ǂ̕����G���R�[�f�B���O�Ɋ�Â��čs�������l����K�v������B
     *
     * �ǂ̕����G���R�[�f�B���O���g���̂��ǂ����͊ȒP�ł͂Ȃ��B
     *
     * �P�Ƀu���E�U-�T�[�o�Ԃ̒ʐM�𒆌p����̂ł���΁A
     * field-content�ɂ��Ă͓��Ɉӎ������ɂ��̂܂܉���������΂悢�B
     * ������s���ɂ͕����G���R�[�f�B���O��ISO-8859-1���g���΂悢�B
     * ���ۂ̃o�C�g�V�[�P���X��MS932��UTF-8�̕����������ꍇ�́A
     * Java��String�Ƃ��Ă͉�����������ɂȂ邪�A�����ISO-8859-1��
     * �o�C�g�V�[�P���X�ɖ߂��ƌ��̃o�C�g�V�[�P���X�Ɠ����ɂȂ�̂ŁA
     * ���������邾���Ȃ���͋N���Ȃ��B
     *
     * �������Afield-content�� URLConnection#addRequestProperty(String, String)
     * �ɓn���ꍇ��ISO-8859-1���Ɩ�肪�N����B
     * ��̓I�ɂ̓u���E�U����̃��N�G�X�g���󂯂āA
     * URLResource�ŃT�[�o�Ƀ��N�G�X�g�𓊂���ꍇ�B
     * URLResouce�A��̓I�ɂ�HttpURLConnection�A����ɂ��̓���������
     * sun.net.NetworkClient ��String���o�C�g�V�[�P���X�ɕϊ�����ۂ�
     * file.encoding���g���B���{��Windows�ł͂����MS932�ɂȂ�B
     * �o�C�g�V�[�P���X��ISO-8859-1��String�ɂ��āA�����MS932�Ńo�C�g�V�[�P���X
     * �ɂ���ƁA���R���Ƃ͈قȂ���̂ɂȂ�B
     * ����String�ɂ���ۂ�MS932���g�����Ƃ��Ă��A
     * UTF-8�̓��{��Ȃǂ�MS932�̑Ή��͈͊O�̃o�C�g���܂܂�Ă����ꍇ��
     * ��񂪎����Ă��܂�(�u?�v�ɒu�������)�A��͂茳�ɂ͖߂�Ȃ��Ȃ�B
     * �ʏ�̑΍�Ƃ��ẮAfile.encoding��ύX���邩�A
     * URLConnection���g���̂���߂邩���������􂪂Ȃ����A
     * �ǂ�����e�����傫����ρB
     *
     * ����ɖ��Ȗ��Ƃ��āARequest-Line��field-content�ɈقȂ�
     * �����G���R�[�f�B���O���g���Ă���ꍇ������B
     * Request-Line��SJIS or MS932�Afield-content��UTF-8�ȂǁB
     * ���̂��߃w�b�_�S�̂�1�̕����G���R�[�f�B���O���g���킯�ɂ͂����Ȃ��B
     *
     * ���p�ł͂Ȃ��ANicoCache���N���C�A���g�Ƃ��ĐU�镑���ꍇ��
     * ��ASCII�������g���K�v�͌��󖳂��̂Ŗ��Ȃ��B
     *
     * ���p�ł͂Ȃ��ANicoCache��origin�T�[�o�Ƃ��ĐU�镑���ꍇ��
     * URLConnection���g��Ȃ��̂ōD���ɕ����G���R�[�f�B���O�����߂���B
     * �e������field-content�͊�{�I��Content-Disposition�̂݁B
     * �u���E�U�ɂ���ĉ��߂ł��镶���R�[�h���قȂ�̂ŁA
     * HttpHeader�̊O������w��ł���K�v������B
     * �efield-content���Ƃɕ����R�[�h���قȂ�\��������̂ŁA
     * String����o�C�g�V�[�P���X�ɕϊ�����ۂ̕����G���R�[�f�B���O��
     * �Ή����邽�߂ɂ͊efield-content���Ƃɕ����G���R�[�f�B���O��
     * �ێ����Ȃ���΂Ȃ�Ȃ��B�����ŁAString����o�C�g�V�[�P���X�ւ�
     * �ϊ���ISO-8859-1�ɌŒ肵�Ă��܂��AsetMessageHeader �̍ۂ�
     * ISO-8859-1�ŕϊ�����Ɩ]�ރo�C�g��ɂȂ鉻����String�Ƃ���
     * �ݒ�ł���悤�ɂ���B
     *
     * ���_�B
     *
     * ���̃N���X���ł́A�o�C�g�V�[�P���X��String�̕ϊ��ɂ�
     * ��񂪎����Ȃ�ISO-8859-1���g���B
     * ��ASCII������������΍�Ƃ��āA�����G���R�[�f�B���O���w�肵��
     * field-content��ǂݏ������郁�\�b�h��p�ӂ���B
     * �w�b�_���o�C�g�V�[�P���X�Ƃ��Ď擾����getBytes()��p�ӂ���B
     * URLConnection�͕ʓrURLConnection���g���Ă���Ƃ���ŉ��Ƃ�����B
     */
    private static final Pattern MULTI_TOKEN_SPLIT =
            Pattern.compile("\\s*,\\s*");
    private static final String ISO_8859_1 = "ISO-8859-1";

    private String startLine = null;
    private HttpMessageHeaderHolder messageHeaders =
            new HttpMessageHeaderHolder();

    /**
     * [nl] RFC2822�`���̓��t/������������擾����B
     * @param time �擾�Ώۂ̃G�|�b�N����̗ݐώ���(�~���b)
     * @return ���t/����������Btime�����̒l�Ȃ�null
     */
    public static String getDateString(long time) {
        if (time < 0) {
            return null;
        }

        DateFormat df = getDateFormatForDateField();
        return df.format(new Date(time));
    }

    /**
     * [nl] ���t/������������p�[�X���ăG�|�b�N����̗ݐώ���(�~���b)�ŕԂ��B
     * @param date ���t/����������(RFC2822)
     * @return �G�|�b�N����̗ݐώ���(�~���b)�B�p�[�X�o���Ȃ����-1
     */
    public static long parseDateString(String date) {
        if (date != null) {
            DateFormat df = getDateFormatForDateField();
            try {
                return df.parse(date).getTime();
            } catch (ParseException e) {
                // ignore
            }
        }

        return -1L;
    }

    // HTTP-date(RFC2822 3.3. Date and Time Specification)
    private static DateFormat getDateFormatForDateField() {
        // RFC2822 allows to omit seconds, but this method does not.
        // "Z" also accepts obsoleted time zone form such as "JST".
        DateFormat df =
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));

        return df;
    }

    public HttpHeader(InputStream source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        init(source);
    }

    public HttpHeader(String source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        init(new ByteArrayInputStream(source.getBytes(ISO_8859_1)));
    }

    private void initByStringImpl(InputStream source) throws IOException, HttpIOException {
        int ch;
        StringBuilder headerString = new StringBuilder(512);
        int lineTopIndex = 0;
        while ((ch = source.read()) != -1) {
            // this results same conversion from byte sequence to String
            // as ISO-8859-1
            headerString.append((char) ch);

            if (ch != '\n') { // go next read() immediately for performance.
                continue;
            }

            String line = headerString.substring(lineTopIndex);

            if (lineTopIndex == 0) {
                // IE sends additional CRLF after POST request.
                // see http://support.microsoft.com/kb/823099/
                // see http://httpd.apache.org/docs/1.3/misc/known_client_problems.html#trailing-crlf
                if (line.equals("\r\n")) {
                    headerString.setLength(0);
                    continue;
                }

                startLine = line;
            } else {
                if (line.equals("\r\n")) {
                    break;
                }

                Matcher m = MESSAGE_HEADER_PATTERN.matcher(line);
                if (m.find()) {
                    messageHeaders.add(m.group(1), m.group(2));
                } else {
                    Logger.warning("invalid header field: " + line);
                }
            }

            lineTopIndex = headerString.length();
        }

        if (lineTopIndex == 0 || ch == -1) {
            throw new HttpIOException("premature end of header: "
                    + headerString);
        }
    }

    private void init(InputStream source) throws IOException, HttpIOException {
        int ch;
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(512);

        while ((ch = source.read()) != -1) {
            lineBuf.write(ch);

            if (ch != '\n') { // go next read() immediately for performance.
                continue;
            }

            // interpret as ISO-8859-1, because it can preserve
            // original byte sequence in Java string.
            String line = lineBuf.toString(ISO_8859_1);

            if (startLine == null) {
                // IE sends additional CRLF after POST request.
                // see http://support.microsoft.com/kb/823099/
                // see http://httpd.apache.org/docs/1.3/misc/known_client_problems.html#trailing-crlf
                if (line.equals("\r\n")) {
                    lineBuf.reset();
                    continue;
                }

                startLine = line;
            } else {
                if (line.equals("\r\n")) {
                    break;
                }

                Matcher m = MESSAGE_HEADER_PATTERN.matcher(line);
                if (m.find()) {
                    messageHeaders.add(m.group(1), m.group(2));
                } else {
                    Logger.warning("invalid header field: " + lineBuf);
                }
            }

            lineBuf.reset();
        }

        if (startLine == null || ch == -1) {
            String header = createIncompleteHeaderString(lineBuf);
            throw new HttpIOException("premature end of header: " + header);
        }
    }

    private String createIncompleteHeaderString(ByteArrayOutputStream lineBuf)
            throws UnsupportedEncodingException {
        String header = messageHeaders.toString();

        if (startLine != null) {
            header = startLine + header;
        }

        if (lineBuf.size() > 0) {
            header = header + lineBuf.toString(ISO_8859_1);
        }

        return header;
    }

    /**
     * �w�b�_�S�̂𕶎���Ƃ��ĕԂ��B
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(startLine);
        result.append(messageHeaders.toString());
        result.append("\r\n");

        return result.toString();
    }

    /**
     * �w�b�_�S�̂��o�C�g�V�[�P���X�Ƃ��ĕԂ��B
     */
    public byte[] getBytes() {
        try {
            return toString().getBytes(ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            // never happen
            throw new IllegalStateException("cannot use " + ISO_8859_1);
        }
    }

    /**
     * Content-Length�̒l��Ԃ��B
     *
     * @return Content-Length�̒l�B���݂��Ȃ������ꍇ��-1�B
     */
    public long getContentLength() {
        String value = getMessageHeader(CONTENT_LENGTH);
        if (value == null) {
            return -1;
        } else {
            return Long.parseLong(value);
        }
    }

    /**
     * Content-Length�̒l��ύX����B
     *
     * @param contentLength
     */
    public void setContentLength(long contentLength) {
        messageHeaders.put(CONTENT_LENGTH, String.valueOf(contentLength));
    }

    /**
     * [nl] Last-Modified�̒l��ݒ肷��B
     * @param time �ݒ肷��G�|�b�N����̗ݐώ���(�~���b)�B���̒l�Ȃ�ݒ肵�Ȃ�
     */
    public void setLastModified(long time) {
        if (time < 0) {
            return;
        }

        DateFormat df = getDateFormatForDateField();
        messageHeaders.put(LAST_MODIFIED, df.format(new Date(time)));
    }

    /**
     * ���b�Z�[�W�w�b�_��Ԃ��Bkey�̑啶���������͓��ꎋ����B
     * ����key�̕����̃��b�Z�[�W�w�b�_������ꍇ�͍Ō�̂��̂�Ԃ��B
     *
     * @param key
     * @return ���b�Z�[�W�w�b�_�B
     */
    public String getMessageHeader(String key) {
        return messageHeaders.get(key);
    }

    /**
     * ���b�Z�[�W�w�b�_���w�肳�ꂽ�����Z�b�g�Ɋ�Â��ăf�R�[�h���ĕԂ��B
     * key�̑啶���������͓��ꎋ����B
     * ����key�̕����̃��b�Z�[�W�w�b�_������ꍇ�͍Ō�̂��̂�Ԃ��B
     *
     * @param key
     * @param charsetName �����Z�b�g���B���݂��Ȃ������Z�b�g�����w�肵���ꍇ��
     * {@link HttpHeader#getMessageHeader(String)}�Ɠ�������B
     * @return ���b�Z�[�W�w�b�_�B
     */
    public String getMessageHeaderOnCharset(String key, String charsetName) {
        String encodedValue = getMessageHeader(key);
        return decodeString(encodedValue, charsetName);
    }

    /**
     * ���b�Z�[�W�w�b�_�S�̂�Ԃ��B
     *
     * @return ���b�Z�[�W�w�b�_�B
     */
    public HttpMessageHeaderHolder getMessageHeaders() {
        return messageHeaders;
    }

    /**
     * ���b�Z�[�W�w�b�_��ݒ肷��B���ɂ���w�b�_���㏑������B
     *
    * @param key �t�B�[���h��
    * @param value �t�B�[���h�l
     */
    public void setMessageHeader(String key, String value) {
        messageHeaders.put(key, value);
    }

    /**
    * ���b�Z�[�W�w�b�_���w�肳�ꂽ�����Z�b�g�Ɋ�Â���
    * �o�C�g�V�[�P���X�Ƃ��Đݒ肷��B
    * ���ɂ���w�b�_���㏑������B
    *
    * @param key �t�B�[���h��
    * @param value �t�B�[���h�l
    * @param charsetName �����Z�b�g���B���݂��Ȃ������Z�b�g�����w�肵���ꍇ��
    * {@link HttpHeader#setMessageHeader(String, String)}�Ɠ�������B
    */
    public void setMessageHeaderOnCharset(String key, String value,
            String charsetName) {
        String encodedValue = encodeString(value, charsetName);

        setMessageHeader(key, encodedValue);
    }

    /**
     * ���b�Z�[�W�w�b�_��ǉ�����B
     * @param key
     * @param value
     */
    public void addMessageHeader(String key, String value) {
        messageHeaders.add(key, value);
    }

    /**
     * ���b�Z�[�W�w�b�_���w�肳�ꂽ�����Z�b�g�Ɋ�Â���
     * �o�C�g�V�[�P���X�Ƃ��Ēǉ�����B
     *
     * @param key �t�B�[���h��
     * @param value �t�B�[���h�l
     * @param charsetName �����Z�b�g���B���݂��Ȃ������Z�b�g�����w�肵���ꍇ��
     * {@link HttpHeader#addMessageHeader(String, String)}�Ɠ�������B
     */
    public void addMessageHeaderOnCharset(String key, String value,
            String charsetName) {
        String encodedValue = encodeString(value, charsetName);

        addMessageHeader(key, encodedValue);
    }

    private String encodeString(String normalStr, String charsetName) {
        String encodedStr;
        try {
            encodedStr =
                    new String(normalStr.getBytes(charsetName), ISO_8859_1);
        } catch (UnsupportedEncodingException e) {
            encodedStr = normalStr;
        }
        return encodedStr;
    }

    private String decodeString(String encodedStr, String charsetName) {
        String normalStr;
        try {
            normalStr =
                    new String(encodedStr.getBytes(ISO_8859_1), charsetName);
        } catch (UnsupportedEncodingException e) {
            normalStr = encodedStr;
        }

        return normalStr;
    }

    /**
     * ���b�Z�[�W�w�b�_���폜����B
     *
     * @param key
     */
    public void removeMessageHeader(String key) {
        messageHeaders.remove(key);
    }

    protected String getStartLine() {
        return startLine;
    }

    protected void setStartLine(String startLine) {
        this.startLine = startLine;
    }

    /**
     * Hop-by-hop Headers���폜����BRFC2616�Œ�`����Ă���͈̂ȉ�:
     *
     * <pre>
     *               - Connection (�Ƃ���ɗ񋓂���Ă������)
     *               - Keep-Alive
     *               - Proxy-Authenticate
     *               - Proxy-Authorization
     *               - TE
     *               - Trailer
     *               - Transfer-Encoding
     *               - Upgrade
     * </pre>
     *
     * �W���ł͂Ȃ���Proxy-Connection���B
     */
    public void removeHopByHopHeaders() {
        removeConnectionAndRelated();

        removeMessageHeader("Keep-Alive");
        // ad hoc treaing for 407
        //removeMessageHeader("Proxy-Authenticate");
        //removeMessageHeader("Proxy-Authorization");
        removeMessageHeader("TE");
        removeMessageHeader("Trailer");
        removeMessageHeader("Transfer-Encoding");
        removeMessageHeader("Upgrade");
        removeMessageHeader("Proxy-Connection");
    }

    protected void removeConnectionAndRelated() {
        String connection = getMessageHeader(CONNECTION);
        if (connection == null) {
            return;
        }

        String[] tokens = MULTI_TOKEN_SPLIT.split(connection);

        for (String token : tokens) {
            removeMessageHeader(token);
        }

        removeMessageHeader(CONNECTION);
    }
}
