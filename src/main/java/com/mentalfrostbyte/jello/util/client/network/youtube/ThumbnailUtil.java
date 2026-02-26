package com.mentalfrostbyte.jello.util.client.network.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.apache.http.ParseException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThumbnailUtil {
    public static YoutubeJPGThumbnail[] search(String searchTags) {
        return extractYoutubeThumbnailsFromHtml(fetchUrlContent("https://www.google.com/search?client=safari&num=21&gbv=1&tbm=vid&oq=&aqs=&q=site%3Ayoutube.com+" + encodeUrlParameter(searchTags)));
    }

    public static YoutubeJPGThumbnail[] getFromChannel(String channelId) {
        return extractYoutubeThumbnails(fetchUrlContent("https://www.youtube.com/@" + channelId + "/videos?disable_polymer=1"));
    }

    public static YoutubeJPGThumbnail[] getFromPlaylist(String playlistId) {
        return extractYoutubeThumbnails(fetchUrlContent("https://www.youtube.com/playlist?list=" + playlistId + "&disable_polymer=1"));
    }

    public static YoutubeJPGThumbnail[] extractYoutubeThumbnails(String htmlContent) {
        if (htmlContent.startsWith("[")) {
            try {
                JsonArray jsonArray = JsonParser.parseString(htmlContent).getAsJsonArray();
                htmlContent = jsonArray.get(1).getAsJsonObject().getAsJsonObject("body").get("content").getAsString();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        List<YoutubeJPGThumbnail> thumbnails = new ArrayList<>();
        Pattern pattern = Pattern.compile("r\":\\{\"videoId\":\"(.{11})\"(.*?)\"text\":\"(.{1,100})\"\\}]", 8);
        Matcher matcher = pattern.matcher(htmlContent.replace("\n", "").replace("\r", ""));

        label36:
        while (matcher.find()) {
            for (YoutubeJPGThumbnail thumbnail : thumbnails) {
                if (thumbnail != null && thumbnail.videoID.equals(matcher.group(1))) {
                    continue label36;
                }
            }

            thumbnails.add(new YoutubeJPGThumbnail(matcher.group(1), decodeAndUnescapeUrl(StringEscapeUtils.unescapeJava(matcher.group(3)))));
        }

        return thumbnails.toArray(new YoutubeJPGThumbnail[0]);
    }

    public static YoutubeJPGThumbnail[] extractYoutubeThumbnailsFromHtml(String htmlContent) {
        if (htmlContent.startsWith("[")) {
            try {
                JsonArray jsonArray = JsonParser.parseString(htmlContent).getAsJsonArray();
                htmlContent = jsonArray.get(1).getAsJsonObject().getAsJsonObject("body").get("content").getAsString();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        ArrayList thumbnails = new ArrayList();
        Pattern pattern = Pattern.compile("<a(.*?)watch%3Fv%3D(.{11})[\\\"&](.*?)><div (.*?)>(.{1,100}) - YouTube<\\/div><\\/h3>", 8);
        Matcher matcher = pattern.matcher(htmlContent.replace("\n", "").replace("\r", ""));

        label62:
        while (matcher.find()) {
            if (!matcher.group(5).contains("</") && !matcher.group(5).equals(" ") && matcher.group(5).length() != 0 && !matcher.group(1).contains("play-all")) {
                for (Object var8 : thumbnails) {
                    if (var8 instanceof YoutubeJPGThumbnail && ((YoutubeJPGThumbnail) var8).videoID.equals(matcher.group(2))) {
                        continue label62;
                    }
                }

                thumbnails.add(new YoutubeJPGThumbnail(matcher.group(2), decodeAndUnescapeUrl(matcher.group(5).replaceAll("<(.*?)>", ""))));
            }
        }

        return (YoutubeJPGThumbnail[]) thumbnails.<YoutubeJPGThumbnail>toArray(new YoutubeJPGThumbnail[0]);
    }

    private static String decodeAndUnescapeUrl(String url) {
        String fullUrl;

        try {
            fullUrl = StringEscapeUtils.unescapeHtml4(URLDecoder.decode(url, StandardCharsets.UTF_8)).trim();
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
            System.out.println(url);
            return "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        }

        return fullUrl;
    }

    private static String encodeUrlParameter(String url) {
        String fullUrl;

		fullUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);

		return fullUrl;
    }

    private static String fetchUrlContent(String url) {
        System.out.println(":" + url);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("ChatCommandExecutor-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

        try {
            CloseableHttpResponse response = client.execute(httpGet);
            Throwable t = null;

            String output;
            try {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return "";
                }

                String stringContent = EntityUtils.toString(entity);
                output = stringContent;
            } catch (Throwable e) {
                t = e;
                throw e;
            } finally {
                if (response != null) {
                    if (t != null) {
                        try {
                            response.close();
                        } catch (Throwable e) {
                            t.addSuppressed(e);
                        }
                    } else {
                        response.close();
                    }
                }
            }

            return output;
        } catch (IllegalStateException | IOException | ParseException e) {
            e.printStackTrace();
            return "";
        }
    }
}
