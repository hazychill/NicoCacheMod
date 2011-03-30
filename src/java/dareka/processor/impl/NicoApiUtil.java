package dareka.processor.impl;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import dareka.common.Logger;

/**
 * Utility class to treat nicovideo API.
 *
 * For maintenance, all of knowledge for nicovideo API should be placed here.
 *
 */
public class NicoApiUtil {
    private NicoApiUtil() {
        // prevent instantiation
    }

    public static String getThumbURL(String type, String id) {
        return "http://ext.nicovideo.jp/api/getthumbinfo/" + type + id;
    }

    public static String getThumbTitle(InputStream thumbResponse) {
        SAXParserFactory f = SAXParserFactory.newInstance();
        try {
            SAXParser p = f.newSAXParser();

            class ThumbTitleHandler extends DefaultHandler {
                public String status = "";

                private boolean inTitle;
                public String title = "";

                /* (非 Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
                 */
                @Override
                public void startElement(String uri, String localName,
                        String name, Attributes attributes) {
                    if (name.equals("nicovideo_thumb_response")) {
                        status = attributes.getValue("status");
                    } else if (name.equals("title")) {
                        inTitle = true;
                    }
                }

                /* (非 Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
                 */
                @Override
                public void endElement(String uri, String localName, String name) {
                    if (name.equals("title")) {
                        inTitle = false;
                    }
                }

                /* (非 Javadoc)
                 * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
                 */
                @Override
                public void characters(char[] ch, int start, int length) {
                    if (inTitle) {
                        StringBuilder newTitle = new StringBuilder(title);
                        newTitle.append(ch, start, length);
                        title = newTitle.toString();
                    }
                }
            }

            ThumbTitleHandler h = new ThumbTitleHandler();
            InputSource i = new InputSource(thumbResponse);
            p.parse(i, h);

            if (!"ok".equals(h.status)) {
                return null;
            }

            return h.title;
        } catch (ParserConfigurationException e) {
            Logger.error(e);
        } catch (SAXException e) {
            Logger.error(e);
        } catch (IOException e) {
            Logger.error(e);
        }
        return null;
    }

    /*
     * 削除時の応答
    <?xml version="1.0" encoding="UTF-8"?>
    <nicovideo_thumb_response status="fail">
    <error>
    <code>DELETED</code>
    <description>deleted</description>
    </error>
    </nicovideo_thumb_response>
     *
     * 成功時の応答
    <?xml version="1.0" encoding="UTF-8"?>
    <nicovideo_thumb_response status="ok">
    <thumb>
    <video_id>sm9</video_id>
    <title>新・豪血寺一族 -煩悩解放 - レッツゴー！陰陽師</title>
    <description>レッツゴー！陰陽師（フルコーラスバージョン）</description>
    <thumbnail_url>http://tn-skr.smilevideo.jp/smile?i=9</thumbnail_url>
    <first_retrieve>2007-03-06T00:33:00+09:00</first_retrieve>
    <length>5:20</length>
    <view_counter>3835975</view_counter>
    <comment_num>2615213</comment_num>
    <mylist_counter>49903</mylist_counter>
    <last_res_body>ギターソロ：エリーザ ギターソロ：ミランダ ギターソロ：リディア ギターソロ：マラリヤ ... </last_res_body>
    <watch_url>http://www.nicovideo.jp/watch/sm9</watch_url>
    <thumb_type>video</thumb_type>
    <tags>
    <tag>陰陽師</tag>
    <tag>レッツゴー！陰陽師</tag>
    <tag>公式</tag>
    <tag>音楽</tag>
    <tag>懼ｧｲ時代の英雄</tag>
    <tag>弾幕推進キャンペーン中</tag>
    <tag>sm最古の動画</tag>
    <tag>sm9</tag>
    </tags>
    </thumb>
    </nicovideo_thumb_response>
     */

}
