package com.joe.waterlite;

import android.graphics.*;
import java.io.*;
import java.net.*;
import org.json.*;

/** Drinking endpoints adapted from abcde2333/NcepuJw IlifeClient (GPL-3.0). */
class IlifeApi {
    static final String BASE = "https://i.ilife798.com/api/v1";
    private final java.util.concurrent.ConcurrentHashMap<HttpURLConnection,Thread> statusConnections=new java.util.concurrent.ConcurrentHashMap<>();
    void cancelStatusRequests(Thread thread){if(thread!=null)for(java.util.Map.Entry<HttpURLConnection,Thread> entry:statusConnections.entrySet())if(entry.getValue()==thread)entry.getKey().disconnect();}
    static final class Result {
        final int code; final String message; final JSONObject json;
        Result(JSONObject json) { this.json=json; code=json.optInt("code", -999); message=json.optString("msg", ""); }
        boolean ok() { return code == 0; }
        boolean expired() { return SessionPolicy.expired(code); }
        JSONObject data() throws Exception { JSONObject d=json.optJSONObject("data"); if(d==null) throw new Exception("服务器响应格式异常，请稍后刷新"); return d; }
        void require() throws Exception { if (!ok()) throw new ApiException(this); }
    }
    static final class ApiException extends Exception {
        final Result result;
        ApiException(Result r) { super(readable(r)); result=r; }
    }
    static String readable(Result r) {
        switch (r.code) {
            case -99: return "登录凭证已失效，请重新验证手机号。已添加的设备会保留。";
            case -88: return "请先在慧生活798完成代扣签约";
            case -87: return "代扣签约已过期，请在慧生活798重新签约";
            case -52: return "余额不足，请在慧生活798充值";
            case -20: return "请先在慧生活798绑定一卡通";
            case -21: return "设备控制登录未被接受，请重新验证手机号";
            case -19: return "设备准备中，请稍后再试";
            case -82: return "需要支付，请在慧生活798处理";
            default: return r.message.isEmpty() ? "服务暂不可用（"+r.code+"），请稍后重试" : r.message;
        }
    }
    static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private HttpURLConnection connection(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "Android_ilife798_8.2.9"); c.setRequestProperty("VersionCode", "8.2.9");
        c.setRequestProperty("Accept-Language", "zh-Hans-CN;q=1"); return c;
    }
    Result request(String method, String path, JSONObject body, String token, String appType) throws Exception {
        HttpURLConnection c=connection(BASE+path);
        boolean statusRequest=path.startsWith("/ui/app/dev/status");
        if(statusRequest)statusConnections.put(c,Thread.currentThread());
        try {
            if(statusRequest && Thread.currentThread().isInterrupted())throw new InterruptedIOException("状态检测已取消");
            c.setRequestMethod(method); c.setRequestProperty("ApplicationType", appType);
            if (!token.isEmpty()) c.setRequestProperty("Authorization", token);
            if (body!=null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); try(OutputStream out=c.getOutputStream()){ out.write(body.toString().getBytes("UTF-8")); } }
            int http=c.getResponseCode();
            if(http<200 || http>=300) throw new IOException("服务器暂不可用（HTTP "+http+"），登录状态已保留");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2_000_000)throw new IOException("服务器响应过大");}
                return new Result(new JSONObject(out.toString("UTF-8")));
            }
        } finally { if(statusRequest)statusConnections.remove(c); c.disconnect(); }
    }
    Bitmap captcha(String key) throws Exception {
        HttpURLConnection c=connection(BASE+"/captcha/?s="+enc(key)+"&r="+System.currentTimeMillis());
        try { if(c.getResponseCode()!=200)throw new IOException("图形验证码加载失败，点击重试"); try(InputStream in=c.getInputStream()){ Bitmap b=BitmapFactory.decodeStream(in); if(b==null)throw new IOException("图形验证码加载失败，点击重试"); return b; } } finally {c.disconnect();}
    }
    Result sms(String phone,String captcha,String key) throws Exception { return request("POST","/acc/login/code",new JSONObject().put("un",phone).put("authCode",captcha).put("s",key),"","1,5"); }
    Result login(String phone,String sms) throws Exception { return request("POST","/acc/login",new JSONObject().put("openCode","").put("un",phone).put("authCode",sms).put("cid",""),"","1,1"); }
    Result account(String token) throws Exception { return request("GET","/acc/view-info",null,token,"1,1"); }
    Result devices(String token) throws Exception { return request("GET","/ui/app/master",null,token,"1,1"); }
    Result wallet(String token) throws Exception { return request("GET","/acc/wallet/owner",null,token,"1,1"); }
    Result status(String token,String id) throws Exception { return request("GET","/ui/app/dev/status?did="+enc(id)+"&more=true&promo=false",null,token,"1,1"); }
    Result control(String token,String id,boolean start) throws Exception { return request("GET",start?"/dev/start?did="+enc(id)+"&upgrade=true&ptype=91&rcp=false":"/dev/end?did="+enc(id),null,token,"1,1"); }
    static int statusValue(Result r) throws Exception { r.require(); JSONObject device=r.data().optJSONObject("device"); JSONObject gene=device==null?null:device.optJSONObject("gene"); if(gene==null || !gene.has("status"))throw new Exception("没有取得设备状态，请稍后刷新"); return gene.getInt("status"); }
}
