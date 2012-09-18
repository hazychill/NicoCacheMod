package dareka.processor.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.Logger;
import dareka.processor.HttpResponseHeader;
import dareka.processor.URLResource;

public class NicoCachingTitleRetriever implements Callable<String> {
    /**
     * 動画タイトルの正規表現
     */
    private static final Pattern HTML_H1_PATTERN = Pattern.compile(
    		"<h1[^>]*>(.+?)</h1>");
    private static final Pattern HTML_TITLE_CLASS_PATTERN = Pattern.compile(
    		"<p class=\"video_title\"[^>]*>(.+?)(?:<a|</p)");
    private static final Pattern HTML_TITLE_VIDEO_PATTERN = Pattern.compile(
    		"var Video = \\{[^\\}]+title:\\s*'(.+?)',\\s");
    private static final Pattern STRIP_TAGS_PATTERN = Pattern.compile(
    		"<.+?>");
	private static final Pattern UTF16_PATTERN = Pattern.compile(
			"\\\\u([0-9A-Fa-f]{4})");
	// <div id="watchAPIDataContainer" style="display:none">(\{.+?\})</div>
	private static final Pattern HTML_JSON_PATTERN = Pattern.compile("<div id=\"watchAPIDataContainer\" style=\"display:none\">(\\{.+?\\})</div>");
	// "title" *: *"(.+?)"
	private static final Pattern HTML_TITLE_IN_JSON_PATTERN = Pattern.compile("\"title\" *: *\"(.+?)\"");

    private String type;
    private String id;

    public NicoCachingTitleRetriever(String type, String id) {
        this.type = type;
        this.id = id;
    }

    public String call() throws Exception {
        Logger.debugWithThread("title retrieving start");

        String url = NicoApiUtil.getThumbURL(type, id);
        URLResource r = new URLResource(url);
        // In the general case, proxy must let browser know a redirection,
        // but in this case, behave just as a client.
        r.setFollowRedirects(true);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        r.transferTo(null, bout, null, null);

        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        new HttpResponseHeader(bin); // skip header

        String title = NicoApiUtil.getThumbTitle(bin);
        Logger.debugWithThread("title retrieving end (" + title + ")");

        NicoIdInfoCache.getInstance().put(type, id, title);

        return title;
    }

    /**
     * HTML文書からタイトルを取得する。
     *
     * @param htmlSource HTML文書
     * @return タイトル。見つからない場合はnull。
     */
    public static String getTitleFromResponse(String htmlSource) {
        if (htmlSource != null) {
			Matcher jsonMatcher = HTML_JSON_PATTERN.matcher(htmlSource);
			if (jsonMatcher.find()) {
				String jsonTextRaw = jsonMatcher.group(1);
				String jsonTextUnescaped = unescape(jsonTextRaw);
				String jsonText = ascii2native(jsonTextUnescaped);
				Matcher titleInJsonMatcher = HTML_TITLE_IN_JSON_PATTERN.matcher(jsonText);
				if (titleInJsonMatcher.find()) {
					String titleInJson = titleInJsonMatcher.group(1);
					return titleInJson;
				}
			}
        	
// 2010/10/14の仕様変更でH1でヘッディングされなくなった
// 念のため <p class="video_title"→Video.title→H1 の順に取得を試みる
        	Matcher mTitle = HTML_TITLE_CLASS_PATTERN.matcher(htmlSource);
        	if (mTitle.find()) {
        		return unescape(stripTags(mTitle.group(1)));
        	} else {
        		Logger.warning("no title matched, use alternative matching...");
        	}
        	mTitle = HTML_TITLE_VIDEO_PATTERN.matcher(htmlSource);
        	if (mTitle.find()) {
        		return ascii2native(mTitle.group(1));
        	}
            mTitle = HTML_H1_PATTERN.matcher(htmlSource);
            if (mTitle.find()) {
                return unescape(stripTags(mTitle.group(1)));
            }
        }
        return null;
    }

	// タイトルに含まれそうなものだけ限定のunescapeHTML()もどき
	public static String unescape(String title) {
		return title.replace("&amp;","&").replace("&apos;", "'").replace("&quot;", "\"");
	}

	// Prototype.jsにある同名関数のパク(ry
	public static String stripTags(String title) {
		Matcher m = STRIP_TAGS_PATTERN.matcher(title);
		if (m.find()) {
			return m.replaceAll("");
		} else {
			return title;
		}
	}

	// \\uXXXXにエンコードされた文字列をUTF-16に戻す(native2asciiの逆)
	public static String ascii2native(String ascii) {
		StringBuffer sb = new StringBuffer(ascii.length());
		Matcher m = UTF16_PATTERN.matcher(ascii);
		while (m.find()) {
			m.appendReplacement(sb, new String(
					Character.toChars(Integer.parseInt(m.group(1), 16))));
		}
		m.appendTail(sb);
		return sb.toString().replaceAll("\\\\([/<>])", "$1");
	}

}
