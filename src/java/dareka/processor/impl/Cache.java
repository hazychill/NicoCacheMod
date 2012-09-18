package dareka.processor.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.Logger;

/**
 *
 * Abstraction of cache handling.
 *
 * Naming specification:
 *
 * A cache is identified by cacheId which consists of letters without '_'
 * (except nltmp).
 *
 * A cache may have a description which consists of letters being available for
 * filename.
 *
 * Mapping of cache names and filename: cacheId + ('_' + description)? + postfix
 *
 */
// TODO Too fragile! Have to be managed the state...
public class Cache {
    private static final String NLTMP = "nltmp";
    private static final String NLTMP_ = "nltmp_";
    private static final String TMP = ".tmp";
    private static final Pattern CACHE_FILE_PATTERN =
            Pattern.compile("^([^_]+?)(?:_.*)?\\.(?!tmp$)[^.]+$");
    private static final Pattern NUMBER_CHARACTER_REFERENCE_PATTERN =
            Pattern.compile("&#(\\d+);");

    private static File cacheDir = new File("cache");
    private static ConcurrentHashMap<String, File> id2File =
            new ConcurrentHashMap<String, File>();
    // [nl] �ꎞ�t�@�C��ID�E�_�E�����[�h��ID
    // TODO id2Tmp, id2DL�̕ύX�ƃt�@�C������͕��s�A�N�Z�X�Ή��v
    private static ConcurrentHashMap<String, File> id2Tmp =
            new ConcurrentHashMap<String, File>();
    private static ConcurrentHashMap<String, Integer> id2DL =
            new ConcurrentHashMap<String, Integer>();

    private String cacheId;
    private String postfix;
    private File cacheFile;
    private File tmpFile;

    public static void init() {
        cacheDir.mkdir();
        id2File.clear();
        id2Tmp.clear();

        searchCachesOnADirectory(cacheDir, 1);
    }

    private static void searchCachesOnADirectory(File dir, int depth) {
        File[] cacheFiles = dir.listFiles();
        if (cacheFiles == null) {
            return;
        }

        for (File file : cacheFiles) {
            if (file.isDirectory()) {
                searchCachesOnADirectory(file, depth + 1);
            } else if (file.isFile()) {
                String id = getIdFromFilename(file.getName());
                if (!id.equals("")) {
                    if (depth == 1 && id.equals(NLTMP)) {
                        id = getIdFromFilename(file.getName().substring(6));
                        id2Tmp.put(id, file);

                        Logger.debug("partial cache found: " + id + " => "
                                + file.getPath());
                    } else {
                        id2File.put(id, file);

                        Logger.debug("cache found: " + id + " => "
                                + file.getPath());
                    }
                }
            }
        }
    }

    static String getIdFromFilename(String filename) {
        Matcher m = CACHE_FILE_PATTERN.matcher(filename);
        if (m.find()) {
            return m.group(1);
        } else {
            return "";
        }
    }

    public static void cleanup() {
        File[] tmpFiles = cacheDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.endsWith(TMP)) {
                    return true;
                }
                return false;
            }
        });

        if (tmpFiles == null) {
            return;
        }

        for (File file : tmpFiles) {
            // directory is also target, but removed only if it's empty.
            file.delete();
        }
    }

    public static long size() {
        long sum = 0;

        for (File file : id2File.values()) {
            sum += file.length();
        }

        return sum;
    }

    public static Map<String, File> getId2File() {
        return Collections.unmodifiableMap(id2File);
    }

    /**
     * [nl] �_�E�����[�h���t���O���Q�b�g
     * @param id
     * @return �_�E�����[�h���Ȃ�true
     */
    public static boolean getDLFlag(String id) {
        Integer size = id2DL.get(id);
        // containsKey()�����Ă���get()�����
        // �r���ő��̃X���b�h�Ɋ��荞�܂��\��������B
        // ConcurrentHashMap�͒l��null�������Ȃ��̂�
        // get()�̌��null�`�F�b�N���邱�Ƃ�
        // �o�^����Ă��邩�ǂ������m�F����B
        // ���������ꂾ���ł͕s�\��

        if (size != null && size.intValue() > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * [nl] �_�E�����[�h���t���O���Z�b�g
     * @param id
     * @param size
     */
    public static void setDLFlag(String id, int size) {
        if (size > 0) {
            id2DL.put(id, Integer.valueOf(size));
        } else {
            id2DL.remove(id);
        }
    }

    public Cache(String cacheId, String postfix) {
        this.cacheId = cacheId;
        this.postfix = postfix;

        cacheFile = id2File.get(cacheId);
        if (cacheFile == null) {
            cacheFile = new File(cacheDir, cacheId + postfix);
        }
    }

    public Cache(String cacheId, String postfix, String desc) {
        this.cacheId = cacheId;
        this.postfix = postfix;

        cacheFile = id2File.get(cacheId);
        if (cacheFile == null) {
            cacheFile = getDescribedCacheFile(desc);
        }
    }

    protected File getDescribedCacheFile(String desc) {
        StringBuffer decodedDesc = new StringBuffer(desc.length());

        Matcher m = NUMBER_CHARACTER_REFERENCE_PATTERN.matcher(desc);

        while (m.find()) {
            m.appendReplacement(decodedDesc,
                    Character.toString((char) Integer.parseInt(m.group(1))));
        }
        m.appendTail(decodedDesc);

        String sanitizedDesc = getSanitizedDescription(decodedDesc.toString());

        return new File(cacheDir, cacheId + '_' + sanitizedDesc + postfix);
    }

    static String getSanitizedDescription(String decodedDesc) {
        String fileNameCharset = System.getProperty("fileNameCharset");

        String narrowedDesc = narrowCharset(decodedDesc, fileNameCharset);

        StringBuilder sanitizedDesc = new StringBuilder(narrowedDesc.length());

        for (int i = 0; i < narrowedDesc.length(); i++) {
            char c = narrowedDesc.charAt(i);
            if (c == '\\' || c == '/' || c == ':' || c == ',' || c == ';'
                    || c == '*' || c == '?' || c == '"' || c == '<' || c == '>'
                    || c == '|') {
                sanitizedDesc.append('-');
            } else {
                sanitizedDesc.append(c);
            }
        }
        return sanitizedDesc.toString();
    }

    static String narrowCharset(String str, String charsetName) {
        String narrowedDesc = str;
        if (!charsetName.equals("")) {
            try {
                narrowedDesc =
                        new String(str.getBytes(charsetName), charsetName);
                // characters which are not supported by the charset
                // become '?'
            } catch (UnsupportedEncodingException e) {
                Logger.warning(e.toString());
            }

        }
        return narrowedDesc;
    }

    public String getId() {
        return cacheId;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public String getCacheFileName() {
        return cacheFile.getName();
    }

    public String getURLString() {
        return cacheFile.toURI().toString();
    }

    public void setDescribe(String desc) {
        cacheFile = getDescribedCacheFile(desc);
    }

    public void setTmpDescribe(String title) throws IOException {
        File newName =
                new File(cacheDir, NLTMP_ + getId() + "_"
                        + getSanitizedDescription(title) + postfix);
      if (renameByCopy(getCacheTmpFile(), newName)) {
            id2Tmp.put(getId(), newName);
        }
    }

	private boolean renameByCopy(File oldFile, File newFile) {
		boolean result;
		boolean copySuccess = copyFile(oldFile, newFile);
		if (copySuccess) {
			result =  oldFile.delete();
		}
		else {
			result = false;
		}

		if (result == false) {
			Logger.warning("rename failed: " + oldFile.getAbsolutePath() + " -> " + newFile.getAbsolutePath());
		}

		return result;
	}

	private boolean copyFile(File srcFile, File dstFile) {
		try {
			if(!dstFile.exists()) {
				dstFile.createNewFile();
			}

			FileChannel source = null;
			FileChannel destination = null;

			try {
				source = new FileInputStream(srcFile).getChannel();
				destination = new FileOutputStream(dstFile).getChannel();
				destination.transferFrom(source, 0, source.size());
				return true;
			}
			finally {
				if(source != null) {
					source.close();
				}
				if(destination != null) {
					destination.close();
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			return true;
		}
	}

	public long length() {
        return cacheFile.length();
    }

    public long tmpLength() throws IOException {
        return getCacheTmpFile().length();
    }

    public boolean exists() {
        return cacheFile.exists();
    }

    public void touch() {
        cacheFile.setLastModified(System.currentTimeMillis());
    }

    public InputStream getInputStream() throws IOException {
        cacheFile.setLastModified(System.currentTimeMillis());
        return new FileInputStream(cacheFile);
    }

    /**
     * �ꎞ�t�@�C���̓��̓X�g���[���𓾂�
     * @return �ꎞ�t�@�C���̓��̓X�g���[��
     * @throws IOException
     */
    public InputStream getTmpInputStream() throws IOException {
        File cacheTmpFile = getCacheTmpFile();
        return new FileInputStream(cacheTmpFile);
    }

    /**
     * �ꎞ�t�@�C���̏o�̓X�g���[���𓾂�
     * @param append �ǋL����ꍇtrue
     * @return �ꎞ�t�@�C���̏o�̓X�g���[��
     * @throws IOException
     */
    public OutputStream getTmpOutputStream(boolean append) throws IOException {
        File cacheTmpFile = getCacheTmpFile();
        return new FileOutputStream(cacheTmpFile, append);
    }

    public void store() throws IOException {
        File cacheTmpFile = getCacheTmpFile();

        // TODO the knowledge of "low" depends on NicoNico, so it should
        // not exist this cache abstraction layer.

        File parentDir = cacheDir;
        if (!cacheId.endsWith("low")) {
            String lowId = cacheId + "low";
            File lowFile = id2File.get(lowId);
            if (lowFile != null) {
                parentDir = lowFile.getParentFile();
            }
        }

        cacheFile = new File(parentDir, cacheFile.getName());

      if (renameByCopy(cacheTmpFile, cacheFile)) {
            id2File.put(cacheId, cacheFile);
        } else {
            cacheTmpFile.delete();
        }

        id2Tmp.remove(cacheId);
    }

    public void deleteTmp() throws IOException {
        File cacheTmpFile = getCacheTmpFile();
        cacheTmpFile.delete();
    }

    // [nl] �ꎞ�t�@�C������ύX����
    // tmpFile��test and set��atomic�ɂ��邽�߂�synchronized���K�v�B
    protected synchronized File getCacheTmpFile() throws IOException {
        if (tmpFile == null) {
            //tmpFile = File.createTempFile(cacheId + ".flv-", TMP, cacheDir);

            File knownTmpFile = id2Tmp.get(getId());
            if (knownTmpFile == null) {
                tmpFile = new File(cacheDir, NLTMP_ + cacheFile.getName());
            } else {
                tmpFile = knownTmpFile;
            }

            if (!tmpFile.exists()) {
                tmpFile.createNewFile();
            }
        }
        id2Tmp.put(getId(), tmpFile);
        return tmpFile;
    }
}
