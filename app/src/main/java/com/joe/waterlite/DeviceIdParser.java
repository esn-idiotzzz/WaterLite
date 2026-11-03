package com.joe.waterlite;

import java.net.URI;
import java.net.URLDecoder;
import java.util.regex.*;

public final class DeviceIdParser {
    private static final Pattern KEY = Pattern.compile("(?:^|[?&#;,\\s{])[\"']?(?:did|deviceId|device_id|id)[\"']?\\s*(?:=|:)\\s*[\"']?([^&#/,}\\s\"']+)", Pattern.CASE_INSENSITIVE);
    private DeviceIdParser() {}
    public static boolean valid(String s) { return s != null && s.matches("[a-zA-Z0-9_-]{4,40}"); }
    public static String parse(String raw) {
        if (raw == null || raw.length() > 4096) return "";
        String s = raw.trim();
        if (valid(s)) return s;
        Matcher m = KEY.matcher(s);
        if (m.find()) {
            try { String id = URLDecoder.decode(m.group(1), "UTF-8"); return valid(id) ? id : ""; }
            catch (Exception ignored) { return ""; }
        }
        try {
            URI uri = new URI(s);
            String host = uri.getHost();
            if (host == null || !(host.equals("hnkzy.com") || host.endsWith(".hnkzy.com") || host.equals("ilife798.com") || host.endsWith(".ilife798.com"))) return "";
            String[] parts = uri.getPath().split("/");
            for (int i = parts.length - 1; i >= 0; i--) if (parts[i].matches("[0-9]{6,32}")) return parts[i];
        } catch (Exception ignored) {}
        return "";
    }
}
