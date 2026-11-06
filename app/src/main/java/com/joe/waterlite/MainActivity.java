package com.joe.waterlite;

import android.app.*;
import android.animation.*;
import android.os.*;
import android.content.*;
import android.content.res.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import android.text.InputType;
import android.text.TextUtils;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    Store store;
    IlifeApi api = new IlifeApi();
    String token="", phone="", message="", wallet="";
    List<Device> devices = new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService statusWorker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation, captchaGeneration;
    private boolean busy, foreground, loginPage;
    private boolean polling;
    private boolean resumeSync=true;
    private volatile int statusGeneration;
    private volatile Thread pollingThread;
    private Future<?> pollFuture;
    private int operationVersion;
    private final Map<String,Button> controlButtons=new HashMap<>();
    private final Map<String,TextView> statusLabels=new HashMap<>();
    private final Map<String,ValueAnimator> controlAnimations=new HashMap<>();
    AppPalette palette;
    private final ArgbEvaluator colorEvaluator=new ArgbEvaluator();
    private final PathInterpolator motionCurve=new PathInterpolator(.2f,0,0,1);
    private final Map<String,SwipeRow> deviceRows=new LinkedHashMap<>();
    private ScrollView deviceScroll;
    private LinearLayout deviceList;
    private View emptyDevices,deviceHint;
    private TextView accountLabel;
    private int renderVersion;
    private Button refreshButton;
    private RotateDrawable refreshGlyph;
    private ObjectAnimator refreshAnimation;
    private boolean refreshing;
    private final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    private boolean checkingUpdate;
    private UpdateClient.Release pendingUpdate;
    private AlertDialog updateDialog;
    private void checkUpdate(){
        if(checkingUpdate)return;checkingUpdate=true;
        updateWorker.execute(()->{UpdateClient.Release found=null;try{found=new UpdateClient().latest();}catch(Exception ignored){}final UpdateClient.Release release=found;handler.post(()->{checkingUpdate=false;if(isDestroyed())return;if(release!=null&&release.newerThan("1.2.0"))pendingUpdate=release;showUpdate();});});
    }
    private void showUpdate(){
        if(!foreground||busy||pendingUpdate==null||updateDialog!=null||pendingAddDialog!=null)return;
        final UpdateClient.Release release=pendingUpdate;pendingUpdate=null;
        ScrollView scroll=new ScrollView(this);TextView notes=text(release.notes.isEmpty()?"此版本未提供更新说明。":release.notes,16,ink);notes.setPadding(dp(24),dp(12),dp(24),dp(12));scroll.addView(notes);
        updateDialog=new AlertDialog.Builder(this).setTitle("发现新版本 "+release.version).setView(scroll).setNegativeButton("取消",null).setPositiveButton("GitHub",(a,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(release.url)));}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器，请打开 GitHub 项目下载",Toast.LENGTH_LONG).show();}}).create();
        updateDialog.setOnDismissListener(d->updateDialog=null);updateDialog.show();updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
    }
    private int bg, card, ink, muted, accent, tint, danger;
    private FrameLayout root;
    private TextView notice;
    private EditText phoneInput, captchaInput, smsInput;
    private Button smsButton, loginButton;
    private ImageView captchaImage;
    private String captchaKey="";
    private boolean captchaReady;
    private long lastPoll;
    private AlertDialog pendingAddDialog;
    private final Runnable ticker=new Runnable(){ public void run(){
        if(!foreground)return;
        updateCooldown();showUpdate();
        if(!loginPage && !busy && !polling && !devices.isEmpty() && SystemClock.elapsedRealtime()-lastPoll>=(hasActive()?1500:8000)) { lastPoll=SystemClock.elapsedRealtime(); pollDevices(); }
        handler.postDelayed(this,500);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new Store(this); phone=store.phone();
        boolean dark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        palette=AppPalette.from(this);bg=palette.background;card=palette.surface;ink=palette.text;
        muted=palette.textSecondary;accent=palette.primary;tint=palette.primaryContainer;danger=palette.error;
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try { token=store.token(); } catch(Exception e){ message="本机登录信息无法读取，请重新验证手机号。设备会保留。"; }
        devices=store.devices(phone);
        if(token.isEmpty() || store.reauth()) showLogin(); else {showDevices();refresh();}
        if(state!=null && loginPage){phoneInput.setText(state.getString("inputPhone",phone));smsInput.setText(state.getString("inputSms",""));}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(loginPage){state.putString("inputPhone",phoneInput.getText().toString());state.putString("inputSms",smsInput.getText().toString());}}
    @Override protected void onResume(){super.onResume();foreground=true;checkUpdate();lastPoll=0;updateRefreshButton();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){foreground=false;resumeSync=true;statusGeneration++;if(pollFuture!=null)pollFuture.cancel(true);api.cancelStatusRequests(pollingThread);polling=false;handler.removeCallbacks(ticker);cancelControlAnimations();updateDeviceViews();updateRefreshButton();for(Button b:controlButtons.values()){b.animate().cancel();b.setScaleX(1);b.setScaleY(1);}for(SwipeRow row:deviceRows.values())row.finishMotion();super.onPause();}
    @Override protected void onDestroy(){generation++;captchaGeneration++;worker.shutdownNow();statusWorker.shutdownNow();updateWorker.shutdownNow();if(updateDialog!=null)updateDialog.dismiss();handler.removeCallbacksAndMessages(null);cancelControlAnimations();if(refreshAnimation!=null)refreshAnimation.cancel();super.onDestroy();}
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int alpha(int color,int opacity){return (color&0x00ffffff)|(opacity<<24);}
    private void setText(TextView view,String text){if(!TextUtils.equals(view.getText(),text))view.setText(text);}
    private boolean animateMotion(){return foreground&&ValueAnimator.areAnimatorsEnabled();}
    private void cancelControlAnimations(){for(Map.Entry<String,ValueAnimator> entry:new ArrayList<>(controlAnimations.entrySet())){entry.getValue().cancel();Button b=controlButtons.get(entry.getKey());if(b!=null)b.setTag(null);}controlAnimations.clear();}
    private Button button(String label, boolean primary, View.OnClickListener click){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(16),0,dp(16),0);
        b.setTextColor(primary?palette.onPrimary:palette.onPrimaryContainer);b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(primary?palette.onPrimary:palette.onPrimaryContainer,36)),shape(primary?accent:tint,24),null));
        b.setOnClickListener(click);return b;
    }
    private void styleControl(String id,Button b,boolean running,boolean animate){
        boolean motion=animate&&animateMotion();
        if(Boolean.valueOf(running).equals(b.getTag())&&(motion||!controlAnimations.containsKey(id)))return;
        ValueAnimator previous=controlAnimations.remove(id);if(previous!=null)previous.cancel();
        GradientDrawable fill=(GradientDrawable)((RippleDrawable)b.getBackground()).getDrawable(0);
        int target=running?danger:accent,onTarget=running?palette.onError:palette.onPrimary;
        int from=fill.getColor().getDefaultColor(),fromText=b.getCurrentTextColor();b.setTag(running);
        if(!motion||from==target){fill.setColor(target);b.setTextColor(onTarget);return;}
        ValueAnimator animation=ValueAnimator.ofArgb(from,target);animation.setDuration(200);animation.setInterpolator(motionCurve);
        animation.addUpdateListener(a->{fill.setColor((Integer)a.getAnimatedValue());b.setTextColor((Integer)colorEvaluator.evaluate(a.getAnimatedFraction(),fromText,onTarget));});
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(controlAnimations.get(id)==a)controlAnimations.remove(id);}});controlAnimations.put(id,animation);animation.start();
    }
    private Button swipeAction(String label,int icon,int color,View.OnClickListener click){
        Button b=button(label,false,click);b.setTextSize(12);b.setTextColor(color);b.setPadding(dp(4),0,dp(4),0);b.setGravity(Gravity.CENTER);b.setStateListAnimator(null);
        android.graphics.drawable.Drawable glyph=getDrawable(icon).mutate();glyph.setTint(color);b.setCompoundDrawablesWithIntrinsicBounds(null,glyph,null,null);b.setCompoundDrawablePadding(dp(8));b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(color,28)),shape(Color.TRANSPARENT,16),null));return b;
    }
    private Button labeledAction(int icon,String label,int id,View.OnClickListener click){
        int fill=getColor(R.color.water_action_fill),glyph=getColor(R.color.water_action_icon);Button b=button(label,false,click);b.setId(id);b.setContentDescription(label);b.setTextColor(glyph);b.setCompoundDrawablePadding(dp(8));Drawable d=getDrawable(icon).mutate();d.setTint(glyph);d.setBounds(0,0,dp(20),dp(20));b.setCompoundDrawablesRelative(d,null,null,null);b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(glyph,28)),shape(fill,28),null));b.setElevation(dp(1));return b;
    }
    private ImageButton actionButton(int icon,String label,int id,View.OnClickListener click){
        int fill=getColor(R.color.water_action_fill),glyph=getColor(R.color.water_action_icon);
        ImageButton b=new ImageButton(this);b.setId(id);b.setContentDescription(label);b.setTooltipText(label);b.setImageResource(icon);b.setImageTintList(ColorStateList.valueOf(glyph));b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(glyph,28)),shape(fill,28),null));b.setElevation(dp(1));b.setOnClickListener(click);return b;
    }
    private void updateRefreshButton(){
        if(refreshButton==null)return;refreshButton.setEnabled(!busy);
        if(refreshing && animateMotion() && (refreshAnimation==null || !refreshAnimation.isRunning())){refreshAnimation=ObjectAnimator.ofInt(refreshGlyph,"level",0,10000);refreshAnimation.setDuration(900);refreshAnimation.setRepeatCount(ValueAnimator.INFINITE);refreshAnimation.setInterpolator(new LinearInterpolator());refreshAnimation.start();}
        else if((!refreshing||!animateMotion()) && refreshAnimation!=null){refreshAnimation.cancel();refreshAnimation=null;refreshGlyph.setLevel(0);}
    }
    private void gap(LinearLayout l,int h){Space v=new Space(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void mount(){
        root=new FrameLayout(this);root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();
    }
    private EditText field(String hint,int type){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(17);e.setTextColor(ink);e.setHintTextColor(muted);e.setHighlightColor(tint);e.setHint(hint);e.setInputType(type);e.setPadding(dp(16),dp(12),dp(16),dp(12));GradientDrawable d=shape(card,12);d.setStroke(dp(1),palette.outline);e.setBackground(d);e.setMinHeight(dp(56));return e;}
    private void label(LinearLayout l,String label){TextView v=text(label,13,muted);v.setPadding(dp(2),0,0,dp(8));l.addView(v);}
    private void showLogin(){
        refreshing=false;updateRefreshButton();refreshButton=null;
        cancelControlAnimations();deviceRows.clear();controlButtons.clear();statusLabels.clear();deviceList=null;deviceScroll=null;
        loginPage=true;mount();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll);
        LinearLayout body=column();body.setPadding(dp(24),dp(24),dp(24),dp(24));scroll.addView(body);
        TextView brand=text("轻饮水",16,accent);brand.setTypeface(null,Typeface.BOLD);body.addView(brand);gap(body,32);
        TextView title=text(store.reauth()?"重新验证手机号":"登录后，轻松接水",30,ink);title.setTypeface(null,Typeface.BOLD);body.addView(title);gap(body,12);
        body.addView(text("使用慧生活798的手机号登录\n你的饮水机，打开就能找到。",16,muted));gap(body,32);
        label(body,"手机号");phoneInput=field("请输入11位手机号",InputType.TYPE_CLASS_PHONE);phoneInput.setText(phone);phoneInput.setId(101);body.addView(phoneInput);gap(body,20);
        label(body,"图形验证码");LinearLayout captchaRow=horizontal();captchaInput=field("输入图中字符",InputType.TYPE_CLASS_TEXT);captchaInput.setId(102);captchaRow.addView(captchaInput,new LinearLayout.LayoutParams(0,dp(56),1));
        captchaImage=new ImageView(this);captchaImage.setBackground(shape(Color.WHITE,12));captchaImage.setContentDescription("图形验证码，点击更换");captchaImage.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(112),dp(56));cp.leftMargin=dp(12);captchaRow.addView(captchaImage,cp);body.addView(captchaRow);captchaImage.setOnClickListener(v->{if(!busy)loadCaptcha();});
        TextView retry=text("点击图片更换验证码",12,muted);retry.setPadding(0,dp(8),0,0);retry.setOnClickListener(v->{if(!busy)loadCaptcha();});body.addView(retry);gap(body,20);
        label(body,"短信验证码");LinearLayout smsRow=horizontal();smsInput=field("输入短信验证码",InputType.TYPE_CLASS_NUMBER);smsInput.setId(103);smsRow.addView(smsInput,new LinearLayout.LayoutParams(0,dp(56),1));
        smsButton=button("获取验证码",false,v->sendSms());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,dp(48));sp.leftMargin=dp(12);smsRow.addView(smsButton,sp);body.addView(smsRow);gap(body,24);
        loginButton=button("登录",true,v->login());body.addView(loginButton,new LinearLayout.LayoutParams(-1,dp(52)));gap(body,16);
        notice=text(message,14,muted);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(notice);gap(body,24);
        body.addView(text("登录信息加密保存在本机\n短信由慧生活798发送。",12,muted));updateCooldown();loadCaptcha();
    }
    private void updateCooldown(){if(!loginPage || smsButton==null)return;long remaining=Math.max(0,(store.cooldown()-System.currentTimeMillis()+999)/1000);setText(smsButton,remaining>0?remaining+"秒后重发":"获取验证码");smsButton.setEnabled(!busy&&remaining==0&&captchaReady);if(loginButton!=null)loginButton.setEnabled(!busy);}
    private void loadCaptcha(){
        final int ticket=++captchaGeneration;final String key=UUID.randomUUID().toString().replace("-","").substring(0,10);captchaKey=key;captchaReady=false;updateCooldown();
        final int epoch=generation;
        worker.execute(()->{try{final android.graphics.Bitmap bmp=api.captcha(key);handler.post(()->{if(epoch!=generation || ticket!=captchaGeneration || !loginPage)return;captchaImage.setImageBitmap(bmp);captchaReady=true;updateCooldown();});}catch(Exception e){handler.post(()->{if(epoch==generation&&ticket==captchaGeneration&&loginPage)setMessage("图形验证码加载失败，点击图片重试。",false);});}});
    }
    private <T> void job(Callable<T> task,Consumer<T> done,Consumer<Exception> failed){
        if(busy)return;busy=true;updateCooldown();updateRefreshButton();final int epoch=generation;
        worker.execute(()->{try{T result=task.call();handler.post(()->{if(epoch!=generation||isFinishing())return;busy=false;done.accept(result);updateCooldown();updateRefreshButton();});}catch(Exception error){handler.post(()->{if(epoch!=generation||isFinishing())return;busy=false;if(!handleExpired(error))failed.accept(error);updateCooldown();updateRefreshButton();});}});
    }
    private boolean handleExpired(Exception error){
        if(!(error instanceof IlifeApi.ApiException) || !((IlifeApi.ApiException)error).result.expired())return false;
        store.expire();message=error.getMessage()+(hasActive()?" 正在接水时请用机身按钮停止。":"");if(pendingAddDialog!=null)pendingAddDialog.dismiss();generation++;showLogin();return true;
    }
    private void setMessage(String s,boolean toast){message=s;if(notice!=null)setText(notice,s);if(toast)Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String safeError(Exception e){if(e instanceof IlifeApi.ApiException)return e.getMessage();if(e instanceof java.net.SocketTimeoutException)return "连接超时，请重试。登录状态已保留。";if(e instanceof java.net.UnknownHostException)return "网络不可用，请检查连接。登录状态已保留。";return e.getMessage()==null?"操作失败，请稍后重试":e.getMessage();}
    private void sendSms(){
        if(busy||!captchaReady||store.cooldown()>System.currentTimeMillis())return;
        final String p=phoneInput.getText().toString().trim(), c=captchaInput.getText().toString().trim(), key=captchaKey;
        if(!p.matches("1[0-9]{10}")){phoneInput.setError("请输入正确的手机号");return;}if(c.isEmpty()){captchaInput.setError("请填写图形验证码");return;}
        job(()->{IlifeApi.Result r=api.sms(p,c,key);r.require();return r;},r->{store.cooldown(System.currentTimeMillis()+60000);setMessage("短信已发送，请查看手机验证码。",false);},e->{setMessage(safeError(e),false);loadCaptcha();});
    }
    private void login(){
        final String p=phoneInput.getText().toString().trim(), code=smsInput.getText().toString().trim();
        if(!p.matches("1[0-9]{10}")){phoneInput.setError("请输入正确的手机号");return;}if(!code.matches("[0-9]{4,8}")){smsInput.setError("请填写短信验证码");return;}
        job(()->{IlifeApi.Result r=api.login(p,code);r.require();JSONObject al=r.data().optJSONObject("al");String t=al==null?"":al.optString("token","");if(t.isEmpty())throw new Exception("登录返回异常，未获取到凭证，请重试");store.login(p,t);return t;},t->{generation++;token=t;phone=p;devices=store.devices(p);message="";wallet="";showDevices();refresh();},e->setMessage(safeError(e),false));
    }
    void showDevices(){
        if(!loginPage&&deviceList!=null){renderDevices();return;}
        cancelControlAnimations();
        if(refreshAnimation!=null){refreshAnimation.cancel();refreshAnimation=null;}
        deviceRows.clear();controlButtons.clear();statusLabels.clear();
        loginPage=false;captchaGeneration++;mount();LinearLayout page=column();root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=horizontal();bar.setPadding(dp(24),dp(8),dp(12),dp(8));TextView app=text("轻饮水",17,accent);app.setTypeface(null,Typeface.BOLD);bar.addView(app,new LinearLayout.LayoutParams(0,dp(48),1));app.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton menu=actionButton(R.drawable.ic_more,"更多选项",106,v->menu(v));bar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(bar);
        deviceScroll=new ScrollView(this);deviceScroll.setClipToPadding(false);page.addView(deviceScroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=column();body.setPadding(dp(24),dp(20),dp(24),dp(188));deviceScroll.addView(body);
        TextView title=text("我的饮水机",30,ink);title.setTypeface(null,Typeface.BOLD);body.addView(title);gap(body,10);
        accountLabel=text("",14,muted);body.addView(accountLabel);gap(body,20);
        notice=text(message,14,muted);notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(notice);gap(body,12);
        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(40),dp(24),dp(40));empty.setBackground(shape(card,24));
        TextView drop=text("◉",48,accent);empty.addView(drop);gap(empty,20);empty.addView(text("还没有饮水机",21,ink));gap(empty,10);TextView help=text("扫描机身二维码，添加常用设备。\n账号收藏也会在刷新后显示。",15,muted);help.setGravity(Gravity.CENTER);empty.addView(help);body.addView(empty);emptyDevices=empty;
        TextView hint=text("左划设备可置顶或删除",12,muted);hint.setPadding(0,0,0,dp(12));body.addView(hint);deviceHint=hint;
        deviceList=column();body.addView(deviceList);
        Button add=labeledAction(R.drawable.ic_scan,"扫码添加",105,v->{if(!busy)startActivityForResult(new Intent(this,ScanActivity.class),200);});add.setEnabled(!busy);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(132),dp(56),Gravity.BOTTOM|Gravity.END);fp.setMargins(dp(24),0,dp(24),dp(24));root.addView(add,fp);
        refreshButton=labeledAction(R.drawable.ic_refresh,"刷新",104,v->refresh());refreshButton.setContentDescription("刷新设备");refreshGlyph=new RotateDrawable();refreshGlyph.setDrawable(getDrawable(R.drawable.ic_refresh));refreshGlyph.setFromDegrees(0);refreshGlyph.setToDegrees(360);refreshGlyph.setBounds(0,0,dp(20),dp(20));refreshGlyph.setTint(getColor(R.color.water_action_icon));refreshButton.setCompoundDrawablesRelative(refreshGlyph,null,null,null);
        FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(132),dp(56),Gravity.BOTTOM|Gravity.END);rp.setMargins(dp(24),0,dp(24),dp(96));root.addView(refreshButton,rp);renderDevices();
    }
    private void renderDevices(){
        setText(accountLabel,maskPhone(phone)+(wallet.isEmpty()?"":"  ·  "+wallet));setText(notice,message);
        emptyDevices.setVisibility(devices.isEmpty()?View.VISIBLE:View.GONE);deviceHint.setVisibility(devices.isEmpty()?View.GONE:View.VISIBLE);
        List<Device> sorted=new ArrayList<>(devices);Collections.sort(sorted,(a,b)->Boolean.compare(b.pinned,a.pinned));
        Set<String> ids=new HashSet<>();for(Device d:sorted)ids.add(d.id);
        final Map<String,Float> oldTops=new HashMap<>();for(Map.Entry<String,SwipeRow> entry:deviceRows.entrySet())oldTops.put(entry.getKey(),entry.getValue().getTop()+entry.getValue().getTranslationY());
        boolean changed=false;
        for(String id:new ArrayList<>(deviceRows.keySet()))if(!ids.contains(id)){SwipeRow row=deviceRows.remove(id);row.animate().cancel();deviceList.removeView(row);controlButtons.remove(id);statusLabels.remove(id);ValueAnimator animation=controlAnimations.remove(id);if(animation!=null)animation.cancel();changed=true;}
        for(int i=0;i<sorted.size();i++){Device d=sorted.get(i);SwipeRow row=deviceRows.get(d.id);if(row==null){row=deviceRow(d);deviceRows.put(d.id,row);}row.device=d;setText(row.title,(d.pinned?"↑  ":"")+d.name);setText(row.pin,d.pinned?"取消置顶":"置顶");
            if(deviceList.indexOfChild(row)!=i){row.animate().cancel();row.setTranslationY(0);if(row.getParent()!=null)deviceList.removeView(row);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);deviceList.addView(row,i,lp);changed=true;}}
        updateDeviceViews();updateRefreshButton();
        if(changed){final int revision=++renderVersion;final int scroll=deviceScroll.getScrollY();final LinearLayout list=deviceList;final ScrollView viewport=deviceScroll;list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){list.getViewTreeObserver().removeOnPreDrawListener(this);if(revision!=renderVersion||list!=deviceList)return true;viewport.scrollTo(0,scroll);for(Map.Entry<String,SwipeRow> entry:deviceRows.entrySet()){SwipeRow row=entry.getValue();Float top=oldTops.get(entry.getKey());if(animateMotion()&&top!=null){row.setTranslationY(top-row.getTop());row.animate().translationY(0).setDuration(220).setInterpolator(motionCurve).start();}}return true;}});}
    }
    private SwipeRow deviceRow(Device d){
        SwipeRow row=new SwipeRow(this);row.device=d;row.setMinimumHeight(dp(128));
        LinearLayout actions=horizontal();actions.setBackground(shape(palette.surfaceVariant,20));actions.setPadding(dp(4),dp(8),dp(4),dp(8));actions.setAlpha(0);actions.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        row.pin=swipeAction(d.pinned?"取消置顶":"置顶",com.joe.waterlite.R.drawable.ic_pin,ink,v->{if(busy)return;row.finishMotion();row.position(0);row.device.pinned=!row.device.pinned;save();showDevices();});Button remove=swipeAction("删除",com.joe.waterlite.R.drawable.ic_delete,danger,v->remove(row.device));actions.addView(row.pin,new LinearLayout.LayoutParams(0,-1,1));actions.addView(remove,new LinearLayout.LayoutParams(0,-1,1));FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(152),-1,Gravity.END);row.addView(actions,ap);row.back=actions;
        LinearLayout content=column();content.setMinimumHeight(dp(128));content.setPadding(dp(18),dp(18),dp(18),dp(18));content.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(ink,20)),shape(card,20),null));
        LinearLayout first=horizontal();row.title=text("",18,ink);row.title.setTypeface(null,Typeface.BOLD);row.title.setMaxLines(2);first.addView(row.title,new LinearLayout.LayoutParams(0,-2,1));
        Button control=button(d.canEnd()?"停止出水":"开始出水",true,v->control(row.device,!row.device.canEnd()));
        styleControl(d.id,control,d.canEnd(),false);control.setOnTouchListener((v,e)->{if(!animateMotion()){v.animate().cancel();v.setScaleX(1);v.setScaleY(1);return false;}if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(90).setInterpolator(motionCurve).start();else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1).scaleY(1).setDuration(150).setInterpolator(motionCurve).start();return false;});
        control.setEnabled(!busy);controlButtons.put(d.id,control);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(48));bp.leftMargin=dp(8);first.addView(control,bp);content.addView(first);gap(content,8);
        TextView sub=text(deviceState(d)+"  ·  "+d.id,13,muted);statusLabels.put(d.id,sub);content.addView(sub);content.setOnLongClickListener(v->{if(busy)return true;new AlertDialog.Builder(this).setTitle(row.device.name).setItems(new String[]{row.device.pinned?"取消置顶":"置顶","修改名称","删除"},(a,n)->{if(busy)return;Device current=row.device;if(n==0){row.finishMotion();row.position(0);current.pinned=!current.pinned;save();showDevices();}else if(n==1)rename(current);else remove(current);}).show();return true;});
        row.addView(content,new FrameLayout.LayoutParams(-1,-2));row.front=content;return row;
    }
    private void menu(View anchor){PopupMenu p=new PopupMenu(this,anchor);p.getMenu().add("手动添加");p.getMenu().add("关于");p.getMenu().add("退出登录");p.setOnMenuItemClickListener(i->{if(busy){setMessage("正在处理，请稍候。",true);return true;}switch(i.getTitle().toString()){case "手动添加":addDialog("");break;case "关于":new AlertDialog.Builder(this).setTitle("轻饮水 1.2.0").setMessage("仅保留慧生活798饮水功能。\n\n登录信息由 Android Keystore 加密保存在本机。相机仅用于扫码。\n\n删除只移除本应用快捷入口，不改变慧生活798的账号收藏。\n\n饮水接口改编自 abcde2333/NcepuJw，GPL-3.0；扫码使用 ZXing，Apache-2.0。\n\n服务端明确令登录凭证失效时，仍需短信重新验证。").setPositiveButton("知道了",null).show();break;case "退出登录":logout();}return true;});p.show();}
    private void logout(){if(hasActive()){setMessage("请先结束正在进行的接水。",true);return;}new AlertDialog.Builder(this).setTitle("退出登录？").setMessage("已添加的饮水机和置顶设置会保留。").setNegativeButton("取消",null).setPositiveButton("退出",(a,w)->{generation++;store.logout();token="";message="";showLogin();}).show();}
    private void remove(Device d){if(busy)return;if(d.canEnd()){setMessage("请先停止这台设备的出水。",true);return;}new AlertDialog.Builder(this).setTitle("删除「"+d.name+"」？").setMessage("移除本应用快捷入口，之后可重新扫码添加。").setNegativeButton("取消",null).setPositiveButton("删除",(a,w)->{try{Set<String> ids=store.hidden(phone);ids.add(d.id);store.hidden(phone,ids);devices.remove(d);store.devices(phone,devices);showDevices();}catch(Exception e){setMessage(safeError(e),true);}}).show();}
    private void rename(Device d){EditText input=field("设备名称",InputType.TYPE_CLASS_TEXT);input.setText(d.name);new AlertDialog.Builder(this).setTitle("修改名称").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",(a,w)->{String name=input.getText().toString().trim();if(!name.isEmpty()){d.name=name;save();showDevices();}}).show();}
    private void save(){try{store.devices(phone,devices);}catch(Exception e){setMessage(safeError(e),true);}}
    static String maskPhone(String p){return p.length()==11?p.substring(0,3)+" ···· "+p.substring(7):p;}
    private boolean hasActive(){for(Device d:devices)if(d.canEnd())return true;return false;}
    private String deviceState(Device d){return d.restored?"正在核实上次接水状态":d.canEnd()?"正在出水":d.status==-1?"正在自动检测状态":"空闲";}
    private void updateDeviceViews(){for(Device d:devices){Button b=controlButtons.get(d.id);if(b!=null){setText(b,d.canEnd()?"停止出水":"开始出水");b.setEnabled(!busy);styleControl(d.id,b,d.canEnd(),true);}TextView label=statusLabels.get(d.id);if(label!=null)setText(label,deviceState(d)+"  ·  "+d.id);}}
    @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(r==200&&result==RESULT_OK&&data!=null&&!loginPage)addDialog(data.getStringExtra("code"));}
    private void addDialog(String raw){
        String parsed=DeviceIdParser.parse(raw);LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),0);EditText id=field("二维码内容或设备编号",InputType.TYPE_CLASS_TEXT);id.setText(parsed.isEmpty()?raw:parsed);box.addView(id);gap(box,16);EditText name=field("设备名称（可选）",InputType.TYPE_CLASS_TEXT);box.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加饮水机").setView(box).setNegativeButton("取消",null).setPositiveButton("添加",null).create();
        pendingAddDialog=dialog;dialog.setOnDismissListener(x->{if(pendingAddDialog==dialog)pendingAddDialog=null;});
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String did=DeviceIdParser.parse(id.getText().toString());if(did.isEmpty()){id.setError("未识别到设备编号，请扫描饮水机机身码");return;}for(Device d:devices)if(d.id.equals(did)){setMessage("这台饮水机已经添加。",true);dialog.dismiss();return;}
            final String t=token,label=name.getText().toString().trim();dialog.getButton(-1).setEnabled(false);job(()->{IlifeApi.Result status=api.status(t,did);int value=IlifeApi.statusValue(status);String serverName=status.data().getJSONObject("device").optString("name","");Device d=new Device(did,label.isEmpty()?serverName:label);d.acceptStatus(value);return d;},d->{try{Set<String> hidden=store.hidden(phone);hidden.remove(did);store.hidden(phone,hidden);devices.add(d);store.devices(phone,devices);message="已添加饮水机";dialog.dismiss();showDevices();}catch(Exception e){dialog.getButton(-1).setEnabled(true);setMessage(safeError(e),true);}},e->{dialog.getButton(-1).setEnabled(true);setMessage(safeError(e),true);});}));dialog.show();
    }
    private static final class Snapshot {List<Device> devices;String wallet="",warning="";}
    void refresh(){
        if(busy||loginPage)return;operationVersion++;final String t=token;final List<Device> cached=new ArrayList<>();for(Device d:devices){Device c=new Device(d.id,d.name);c.pinned=d.pinned;c.ownStart=d.ownStart;c.running=d.running;c.restored=d.restored;c.idleChecks=d.idleChecks;cached.add(c);}final Set<String> hidden=store.hidden(phone);
        setMessage("正在刷新…",false);
        refreshing=true;
        job(()->{
            Snapshot s=new Snapshot();s.devices=cached;
            IlifeApi.Result account=api.account(t);account.require();
            try{IlifeApi.Result list=api.devices(t);list.require();JSONArray favos=list.data().optJSONArray("favos");if(favos==null)throw new Exception("设备列表响应异常");for(int i=0;i<favos.length();i++){JSONObject o=favos.getJSONObject(i);String id=o.optString("id","");if(!DeviceIdParser.valid(id)||hidden.contains(id))continue;boolean exists=false;for(Device d:cached)if(d.id.equals(id))exists=true;if(!exists)cached.add(new Device(id,o.optString("name","")));}}catch(IlifeApi.ApiException e){if(e.result.expired())throw e;s.warning="部分数据未更新，请稍后刷新。";}catch(Exception e){s.warning="部分数据未更新，请稍后刷新。";}
            for(Device d:cached){try{d.acceptStatus(IlifeApi.statusValue(api.status(t,d.id)));}catch(IlifeApi.ApiException e){if(e.result.expired())throw e;d.idleChecks=0;d.status=-1;s.warning="部分设备状态暂未更新，正在自动重试。";}catch(Exception e){d.idleChecks=0;d.status=-1;s.warning="部分设备状态暂未更新，正在自动重试。";}}
            try{IlifeApi.Result r=api.wallet(t);r.require();JSONObject data=r.data();List<JSONObject> wallets=new ArrayList<>();if(data.optJSONObject("aw")!=null)wallets.add(data.getJSONObject("aw"));JSONArray eps=data.optJSONArray("eps");if(eps!=null)for(int i=0;i<eps.length();i++)wallets.add(eps.getJSONObject(i));LinkedHashSet<String> seen=new LinkedHashSet<>();StringBuilder out=new StringBuilder();for(JSONObject w:wallets){JSONObject ep=w.optJSONObject("ep");if(ep==null||!seen.add(ep.optString("id")))continue;double amount=w.optDouble("total",w.optDouble("olCash",w.optDouble("balance",Double.NaN)));if(Double.isNaN(amount))continue;if(out.length()>0)out.append(" / ");out.append(ep.optString("name","余额")).append(" ¥").append(String.format(Locale.CHINA,"%.2f",amount));}s.wallet=out.toString();}catch(IlifeApi.ApiException e){if(e.result.expired())throw e;}catch(Exception ignored){}
            return s;
        },s->{refreshing=false;lastPoll=SystemClock.elapsedRealtime();devices=s.devices;wallet=s.wallet;message=s.warning;save();showDevices();},e->{refreshing=false;for(Device d:devices){d.status=-1;d.checkedAt=0;}wallet="";message=safeError(e);showDevices();});
    }
    private void control(Device d,boolean start){
        if(busy)return;operationVersion++;final String t=token;final boolean wasRunning=d.running;final long previousStart=d.ownStart;
        d.running=start;d.ownStart=start?System.currentTimeMillis():0;d.idleChecks=0;d.status=start?0:99;
        setMessage(start?"正在开启设备…":"正在结束出水…",false);
        job(()->{IlifeApi.Result r=api.control(t,d.id,start);r.require();return r;},r->{d.checkedAt=System.currentTimeMillis();lastPoll=SystemClock.elapsedRealtime();setMessage(start?"设备已启动，请接水。":"已结束出水",false);save();updateDeviceViews();},e->{if(e instanceof IlifeApi.ApiException){d.running=wasRunning;d.ownStart=previousStart;d.status=wasRunning?0:99;setMessage(safeError(e),false);}else{d.running=start||wasRunning;d.ownStart=d.running?System.currentTimeMillis():0;setMessage("操作结果尚未确认，正在自动检测；可用机身按钮停止出水。",false);}save();updateDeviceViews();});
        updateDeviceViews();
    }
    private void pollDevices(){
        final List<String> ids=new ArrayList<>();for(Device d:devices)ids.add(d.id);final String t=token;final int epoch=generation,revision=operationVersion,ticket=statusGeneration;final boolean reconcile=resumeSync;polling=true;
        pollFuture=statusWorker.submit(()->{pollingThread=Thread.currentThread();Map<String,Integer> states=new HashMap<>();Exception expired=null;
            for(String id:ids){if(ticket!=statusGeneration||Thread.currentThread().isInterrupted())break;try{states.put(id,IlifeApi.statusValue(api.status(t,id)));}catch(IlifeApi.ApiException e){if(e.result.expired()){expired=e;break;}}catch(Exception ignored){}}
            final Exception error=expired;handler.post(()->{if(ticket!=statusGeneration)return;polling=false;if(epoch!=generation||revision!=operationVersion||!foreground||busy||isFinishing())return;if(error!=null){handleExpired(error);return;}
                List<String> stopped=new ArrayList<>();for(Device d:devices){if(states.containsKey(d.id)){boolean wasRunning=d.running;d.acceptStatus(states.get(d.id),reconcile);if(wasRunning&&!d.running)stopped.add(d.id);}else d.idleChecks=0;}
                if(states.size()==ids.size())resumeSync=false;
                if(!stopped.isEmpty()){
                    setMessage("设备已自动停止出水",false);
                    job(()->{for(String id:stopped){IlifeApi.Result r=api.control(t,id,false);if(r.expired())r.require();}return null;},r->updateDeviceViews(),e->updateDeviceViews());
                }else if(states.size()!=ids.size())setMessage("设备状态暂未更新，正在自动重试。",false);else if(message.equals("设备状态暂未更新，正在自动重试。"))setMessage("",false);
                save();updateDeviceViews();
            });
        });
    }
    private final class SwipeRow extends FrameLayout {
        Device device;TextView title;Button pin;
        View front,back;float downX,downY,start;boolean dragging;ValueAnimator settling;VelocityTracker velocity;
        final int touchSlop,minimumFling,maximumFling;
        SwipeRow(Context c){super(c);setBackground(shape(card,20));setClipToOutline(true);ViewConfiguration configuration=ViewConfiguration.get(c);touchSlop=configuration.getScaledTouchSlop();minimumFling=configuration.getScaledMinimumFlingVelocity();maximumFling=configuration.getScaledMaximumFlingVelocity();}
        private void position(float x){front.setTranslationX(x);float progress=Math.min(1,Math.abs(x)/dp(152));back.setAlpha(progress);back.setTranslationX(dp(12)*(1-progress));int importance=progress>.8f?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;if(back.getImportantForAccessibility()!=importance)back.setImportantForAccessibility(importance);}
        private void recycleVelocity(){if(velocity!=null){velocity.recycle();velocity=null;}}
        private void finishMotion(){if(settling!=null){settling.cancel();settling=null;}animate().cancel();setTranslationY(0);if(front!=null)position(front.getTranslationX()<-dp(76)?-dp(152):0);dragging=false;recycleVelocity();}
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e){
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){if(settling!=null)settling.cancel();recycleVelocity();velocity=VelocityTracker.obtain();downX=e.getX();downY=e.getY();start=front.getTranslationX();dragging=false;}
            if(velocity!=null)velocity.addMovement(e);
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE && !busy && Math.abs(e.getX()-downX)>touchSlop && Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)*1.3f){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(!dragging&&(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL))recycleVelocity();return false;
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(!dragging)return super.onTouchEvent(e);if(velocity!=null)velocity.addMovement(e);if(e.getActionMasked()==MotionEvent.ACTION_MOVE){position(Math.max(-dp(152),Math.min(0,start+e.getX()-downX)));return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000,maximumFling);speed=velocity.getXVelocity();}float target=speed<-minimumFling?-dp(152):speed>minimumFling?0:front.getTranslationX()<-dp(76)?-dp(152):0;recycleVelocity();dragging=false;if(!animateMotion()||Math.abs(front.getTranslationX()-target)<1){position(target);return true;}settling=ValueAnimator.ofFloat(front.getTranslationX(),target);settling.setDuration(220);settling.setInterpolator(motionCurve);settling.addUpdateListener(a->position((Float)a.getAnimatedValue()));settling.start();return true;}return true;}
        @Override protected void onDetachedFromWindow(){finishMotion();super.onDetachedFromWindow();}
    }
}
