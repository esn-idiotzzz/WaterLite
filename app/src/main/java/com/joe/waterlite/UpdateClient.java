package com.joe.waterlite;

import java.net.*;
import java.io.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import android.text.Html;

/** Public release metadata only; never attaches the drinking account token. */
final class UpdateClient {
    static final String PROJECT="https://github.com/zhouyangxin13-ai/WaterLite";
    static final class Release {
        final String version,notes,url;
        Release(String version,String notes){this.version=version;this.notes=notes;url=PROJECT+"/releases/tag/"+version;}
        boolean newerThan(String current){int[] a=parts(version),b=parts(current);if(a==null||b==null)return false;for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];return false;}
    }
    static int[] parts(String value){if(!value.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+"))return null;try{String[] p=value.replaceFirst("^v","").split("\\.");return new int[]{Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])};}catch(NumberFormatException e){return null;}}
    static Release parse(JSONObject json) throws Exception {
        String tag=json.optString("tag_name");if(json.optBoolean("draft")||json.optBoolean("prerelease")||parts(tag)==null)return null;
        JSONArray assets=json.optJSONArray("assets");boolean apk=false;
        if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(a.optString("name").endsWith(".apk")&&a.optLong("size")>0&&a.optString("browser_download_url").startsWith(PROJECT+"/releases/download/"+tag+"/"))apk=true;}
        return apk?new Release(tag,json.optString("body")):null;
    }
    Release latest() throws Exception {
        try{return parse(new JSONObject(read("https://api.github.com/repos/zhouyangxin13-ai/WaterLite/releases/latest")));}
        catch(IOException e){
            // Atom supplies both version and notes when GitHub's unauthenticated API is rate limited.
            XmlPullParser parser=Xml.newPullParser();parser.setInput(new StringReader(read(PROJECT+"/releases.atom")));
            String tag=null,notes="";boolean entry=false;
            for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
                if(event==XmlPullParser.START_TAG){String name=parser.getName();if(name.equals("entry")){entry=true;tag=null;notes="";}
                    else if(entry&&name.equals("link")){String link=parser.getAttributeValue(null,"href");String prefix=PROJECT+"/releases/tag/";if(link!=null&&link.startsWith(prefix))tag=link.substring(prefix.length());}
                    else if(entry&&name.equals("content"))notes=Html.fromHtml(parser.nextText(),Html.FROM_HTML_MODE_LEGACY).toString();
                }else if(event==XmlPullParser.END_TAG&&parser.getName().equals("entry")){entry=false;if(tag!=null&&parts(tag)!=null)return new Release(tag,notes);}
            }return null;
        }
    }
    private String read(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("User-Agent","WaterLite/1.2.0");c.setRequestProperty("Accept","application/vnd.github+json, application/atom+xml");
        try{if(c.getResponseCode()!=200)throw new IOException("Update metadata unavailable");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2_000_000)throw new IOException("Update metadata too large");}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
}
