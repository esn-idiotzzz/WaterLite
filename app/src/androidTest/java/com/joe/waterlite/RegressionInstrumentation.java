package com.joe.waterlite;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import com.google.zxing.*;
import com.google.zxing.common.*;

/** Separate test APK only. It never sends login, SMS or device-control requests to the service. */
public class RegressionInstrumentation extends Instrumentation {
    private MainActivity activity;
    private String mode;
    private MockApi startupMock;
    @Override public Activity newActivity(ClassLoader loader,String name,Intent intent) throws InstantiationException,IllegalAccessException,ClassNotFoundException { if(startupMock!=null&&name.equals(MainActivity.class.getName())){MainActivity a=new MainActivity();a.api=startupMock;return a;}return super.newActivity(loader,name,intent);}
    private final List<String> passed=new ArrayList<>();
    private void check(boolean condition,String name){if(!condition)throw new AssertionError(name);passed.add(name);}
    @Override public void onCreate(Bundle b){super.onCreate(b);mode=b==null?null:b.getString("mode");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(mode!=null){
            Store persisted=new Store(getTargetContext());
            if(mode.equals("updates")){UpdateClient.Release latest=new UpdateClient().latest();check(latest!=null,"Public update metadata includes stable release with APK");check(latest.url.startsWith("https://github.com/esn-idiotzzz/WaterLite/releases/tag/"),"Update metadata uses current account URL");} else if(mode.equals("seed")){persisted.login("18800000000","cold-fixture-token");Device d=new Device("12345678","cold fixture");d.running=true;d.ownStart=System.currentTimeMillis()-30000;persisted.devices("18800000000",Collections.singletonList(d));Thread.sleep(200);}
            else {check(persisted.token().equals("cold-fixture-token"),"Encrypted session survives force-stop and process restart");startupMock=new MockApi();Intent launch=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(launch);awaitJob();check(!activity.devices.get(0).canEnd()&&!activity.devices.get(0).restored,"Killed process resumes with idle hardware after first fresh status");check(startupMock.statusReads==1,"Cold-start idle recovery does not wait five polling rounds");}
            result.putString("stream","PASS cold-process "+mode+" "+passed.size()+" checks\n"+String.join("\n",passed));finish(Activity.RESULT_OK,result);return;
        }

        android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(service);
        Store store=new Store(getTargetContext());store.logout();store.devices("18800000000",Collections.emptyList());store.hidden("18800000000",Collections.emptySet());
        Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
        check(findText("登录后，轻松接水")!=null,"No session opens login screen");
        Thread.sleep(350);screenshot("qa-login.png");
        MockApi mock=new MockApi();
        runOnMainSync(()->activity.api=mock);
        store.login("18800000000","fixture-secret-token");
        check(store.token().equals("fixture-secret-token"),"Keystore encryption round trip");
        check(!getTargetContext().getSharedPreferences("waterlite",0).getString("session","").contains("fixture-secret-token"),"Token is not stored in plaintext");
        check(DeviceIdParser.parse("https://i.ilife798.com/?did=12345678").equals("12345678"),"QR query DID parser");
        check(DeviceIdParser.parse("{\"deviceId\":\"87654321\"}").equals("87654321"),"QR JSON DID parser");
        check(DeviceIdParser.parse("https://hnkzy.com/dev/12345678").equals("12345678"),"QR supported path parser");
        check(DeviceIdParser.parse("https://unrelated.example/12345678").isEmpty(),"Unrelated QR rejected");
        check(DeviceIdParser.parse("x?did=../../bad").isEmpty(),"Malformed DID rejected");
        String qr="https://i.ilife798.com/?did=12345678";BitMatrix matrix=new MultiFormatWriter().encode(qr,BarcodeFormat.QR_CODE,360,360);byte[] frame=new byte[360*360];for(int y=0;y<360;y++)for(int x=0;x<360;x++)frame[y*360+x]=(byte)(matrix.get(x,y)?0:255);
        PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(frame,360,360,0,0,360,360,false);check(new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText().equals(qr),"QR decoder synthetic camera frame");
        runOnMainSync(()->{((EditText)activity.findViewById(101)).setText("18800000000");((EditText)activity.findViewById(103)).setText("123456");find(activity.getWindow().getDecorView(),"登录",false).performClick();});awaitJob();
        check(store.token().equals("new-fixture-token"),"SMS login UI stores received token");
        check(findText("我的饮水机")!=null && activity.devices.size()==2,"Authenticated device list sync");
        check(mock.correctPlatform,"Control token uses platform 1,1");
        verify120();
        verifyPalette();
        runOnMainSync(()->{int fill=activity.getColor(R.color.water_action_fill);for(int id:new int[]{104,105,106}){View b=activity.findViewById(id);android.graphics.drawable.GradientDrawable shape=(android.graphics.drawable.GradientDrawable)((android.graphics.drawable.RippleDrawable)b.getBackground()).getDrawable(0);check(shape.getColor().getDefaultColor()==fill,"Circular action shares day/night fill "+id);}});
        final int[] refreshPosition=new int[2],scanPosition=new int[2];runOnMainSync(()->{View icon=activity.findViewById(104);check(icon instanceof Button && "刷新设备".contentEquals(icon.getContentDescription()),"Refresh is an accessible labeled icon button");icon.getLocationOnScreen(refreshPosition);activity.findViewById(105).getLocationOnScreen(scanPosition);});
        check(Math.abs(refreshPosition[0]-scanPosition[0])<4 && refreshPosition[1]<scanPosition[1] && activity.findViewById(104).getWidth()==activity.findViewById(105).getWidth(),"Refresh is above scan with matching width and right alignment");
        int accountReads=mock.accountReads;runOnMainSync(()->{activity.findViewById(104).performClick();check(booleanField("refreshing")&&!activity.findViewById(104).isEnabled(),"Refresh icon shows an in-progress state");});awaitJob();check(mock.accountReads>accountReads&&!booleanFieldOnMain("refreshing"),"Refresh icon reloads devices and ends progress state");
        runOnMainSync(()->activity.findViewById(106).performClick());waitForIdleSync();check(!accessibleText("刷新设备"),"Refresh entry removed from overflow menu");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        Thread.sleep(250);check(buttonColor("开始出水")==activity.palette.primary,"Start button uses MIUI primary color");
        mock.lagIdleReads=2;
        runOnMainSync(()->{find(activity.getWindow().getDecorView(),"开始出水",false).performClick();check(find(activity.getWindow().getDecorView(),"停止出水",false)!=null,"Start switches button immediately while request is pending");});awaitJob();
        check(activity.devices.get(0).canEnd() && mock.starts==1 && findText("停止出水")!=null,"One tap starts water and changes button without confirmation");
        awaitCondition(()->activity.devices.get(0).idleChecks==1,5000);
        check(activity.devices.get(0).canEnd() && findText("停止出水")!=null,"First delayed idle response does not hide stop button");
        check(buttonColor("停止出水")==activity.palette.error,"Stop button transitions to theme error color");screenshot("qa-running.png");
        awaitCondition(()->activity.devices.get(0).idleChecks==2,5000);
        check(activity.devices.get(0).canEnd(),"Repeated startup status lag is tolerated");
        mock.statusGate=new CountDownLatch(1);mock.statusEntered=new CountDownLatch(1);
        check(mock.statusEntered.await(5,TimeUnit.SECONDS),"Automatic status check starts without manual refresh");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();check(!activity.devices.get(0).canEnd() && mock.ends==1 && findText("开始出水")!=null,"One tap sends real end endpoint even while status request is pending");
        mock.statusGate.countDown();awaitCondition(()->!booleanField("polling"),3000);mock.statusGate=null;
        check(!activity.devices.get(0).canEnd(),"Older running response cannot undo successful stop");
        Thread.sleep(250);check(buttonColor("开始出水")==activity.palette.primary,"Stopped button transitions back to MIUI primary");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();mock.machineStatus=99;
        awaitCondition(()->!activity.devices.get(0).canEnd(),12000);awaitJob();
        check(findText("开始出水")!=null && mock.ends==2,"Automatic hardware stop restores start and closes session without refresh");
        mock.machineStatus=0;awaitCondition(()->activity.devices.get(0).canEnd(),11000);
        check(activity.devices.get(0).ownStart==0 && findText("停止出水")!=null,"Running device detected automatically without a local start marker");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();check(mock.ends==3,"Detected running device can be stopped with same end endpoint");
        mock.startCode=-19;runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();check(!activity.devices.get(0).canEnd() && findText("开始出水")!=null,"Rejected start restores start button");mock.startCode=0;
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();mock.stopCode=-19;
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();check(activity.devices.get(0).canEnd() && findText("停止出水")!=null,"Rejected stop remains stoppable instead of claiming idle");mock.stopCode=0;
        mock.statusFailure=true;int reads=mock.statusReads;awaitCondition(()->mock.statusReads>reads,5000);awaitCondition(()->!booleanField("polling"),3000);
        check(activity.devices.get(0).canEnd() && findText("停止出水")!=null,"Temporary polling failure preserves stop button");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();mock.statusFailure=false;
        runOnMainSync(()->{find(activity.getWindow().getDecorView(),"开始出水",false).performClick();activity.devices.get(0).ownStart=System.currentTimeMillis()-30_000;});awaitJob();
        background();mock.machineStatus=99;
        returnToActivity();
        awaitCondition(()->booleanField("foreground"),3000);awaitCondition(()->!activity.devices.get(0).canEnd(),2000);awaitJob();
        check(findText("开始出水")!=null,"Returning from background reconciles stopped hardware in one fresh check");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();final int beforeResume=mock.statusReads;
        backgroundAndReturn();awaitCondition(()->mock.statusReads>beforeResume&&!booleanField("polling"),2500);
        check(activity.devices.get(0).canEnd(),"Still-running hardware remains stoppable on resume");
        mock.statusGate=new CountDownLatch(1);mock.statusEntered=new CountDownLatch(1);check(mock.statusEntered.await(5,TimeUnit.SECONDS),"Slow status request is active before backgrounding");
        runOnMainSync(()->activity.devices.get(0).ownStart=System.currentTimeMillis()-30_000);background();mock.statusGate=null;mock.machineStatus=99;
        returnToActivity();awaitCondition(()->!activity.devices.get(0).canEnd(),2000);awaitJob();check(findText("开始出水")!=null,"Previous background request cannot delay fresh resume status");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();mock.lagIdleReads=1;
        backgroundAndReturn();awaitCondition(()->activity.devices.get(0).idleChecks==1,2500);check(activity.devices.get(0).canEnd(),"Quick background return preserves startup idle protection");
        mock.lagIdleReads=0;runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();
        runOnMainSync(()->{find(activity.getWindow().getDecorView(),"开始出水",false).performClick();activity.devices.get(0).ownStart=System.currentTimeMillis()-30_000;});awaitJob();mock.statusFailure=true;final int readsBeforeFailure=mock.statusReads;
        backgroundAndReturn();awaitCondition(()->mock.statusReads>readsBeforeFailure&&!booleanField("polling"),2500);check(activity.devices.get(0).canEnd(),"Resume network failure never pretends hardware stopped");
        mock.statusFailure=false;mock.machineStatus=99;awaitCondition(()->!activity.devices.get(0).canEnd(),3500);awaitJob();check(findText("开始出水")!=null,"Failed resume check retries and recovers without five extra rounds");
        check(findText("测试钱包 ¥12.34")!=null || contains("测试钱包 ¥12.34"),"Wallet amount parsed");
        mock.accountCode=-19;runOnMainSync(()->activity.refresh());awaitJob();check(findText("我的饮水机")!=null && store.token().equals("new-fixture-token") && !store.reauth(),"Temporary API error keeps encrypted session and list");
        mock.accountCode=0;mock.transportFailure=true;runOnMainSync(()->activity.refresh());awaitJob();check(findText("我的饮水机")!=null && !store.reauth(),"Network failure keeps session");
        mock.transportFailure=false;mock.accountCode=-99;runOnMainSync(()->activity.refresh());awaitJob();check(findText("重新验证手机号")!=null && store.reauth() && !store.token().isEmpty(),"Only explicit expiry requests reauthentication");
        check(store.devices("18800000000").size()==2,"Devices survive expiry");
        store.login("18800000000","new-fixture-token");check(!store.reauth(),"New login clears expiry flag");
        Device pinned=store.devices("18800000000").get(0);pinned.pinned=true;store.devices("18800000000",Arrays.asList(pinned));check(new Store(getTargetContext()).devices("18800000000").get(0).pinned,"Pin persists across store recreation");
        store.hidden("18800000000",new HashSet<>(Collections.singleton("87654321")));
        mock.accountCode=0;runOnMainSync(()->{activity.token="new-fixture-token";activity.devices=store.devices(activity.phone);activity.showDevices();activity.refresh();});awaitJob();
        check(activity.devices.size()==1,"Deleted remote favorite does not return after sync");
        check(store.devices("19900000000").isEmpty(),"Device data isolated by phone account");
        verifyDisabledAnimations();
        verifyStableDeviceList();
        // Leave an explicitly labelled test scene for screenshot QA; no fixtures exist in the deliverable APK.
        runOnMainSync(()->{activity.devices.clear();Device a=new Device("12345678","宿舍楼 · 一层");a.status=99;a.pinned=true;Device b=new Device("87654321","图书馆 · 二层");b.status=99;activity.devices.add(a);activity.devices.add(b);activity.wallet="测试钱包 ¥12.34";activity.message="界面测试数据";activity.showDevices();});
        waitForIdleSync();screenshot("qa-devices.png");
        final View[] row={null};final int[] pos=new int[2];runOnMainSync(()->{View v=find(activity.getWindow().getDecorView(),"宿舍楼",true);while(v!=null&&!v.getClass().getSimpleName().equals("SwipeRow"))v=(View)v.getParent();row[0]=v;v.getLocationOnScreen(pos);});
        long down=SystemClock.uptimeMillis();float x1=pos[0]+row[0].getWidth()*.85f,x2=pos[0]+row[0].getWidth()*.2f,y=pos[1]+row[0].getHeight()*.5f;
        sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x1,y,0));for(int i=1;i<=8;i++){Thread.sleep(30);sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x1+(x2-x1)*i/8,y,0));}sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x2,y,0));Thread.sleep(250);waitForIdleSync();
        final boolean[] revealed={false};runOnMainSync(()->revealed[0]=((ViewGroup)row[0]).getChildAt(1).getTranslationX()<0 && ((ViewGroup)row[0]).getChildAt(0).getAlpha()>.95f);check(revealed[0],"Left swipe smoothly reveals device actions");screenshot("qa-swipe.png");
        runOnMainSync(()->((ViewGroup)((ViewGroup)row[0]).getChildAt(0)).getChildAt(0).performClick());check(!activity.devices.get(0).pinned,"Swipe pin action toggles pin");
        runOnMainSync(()->find(activity.getWindow().getDecorView(),"删除",false).performClick());waitForIdleSync();clickDialog("删除");waitForIdleSync();check(activity.devices.size()==1 && store.hidden("18800000000").contains("12345678"),"Confirmed delete removes and persistently hides device");
        result.putString("stream","PASS "+passed.size()+" checks\n"+String.join("\n",passed));finish(Activity.RESULT_OK,result);
    }catch(Throwable e){java.io.StringWriter trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));try{screenshot("qa-failure.png");}catch(Exception ignored){}result.putString("stream","FAILED: "+trace+"\nPassed: "+passed);finish(Activity.RESULT_CANCELED,result);}}
    private void verify120() throws Exception {
        check(IlifeApi.loginSignature("18800000000","123456","test-salt").equals("e8a2f17a234e8915c3d631c919e52225"),"App login signature matches protocol vector");
        java.lang.reflect.Method connection=IlifeApi.class.getDeclaredMethod("connection",String.class);connection.setAccessible(true);
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)connection.invoke(new IlifeApi(),"https://example.com");
        check("3.1.9".equals(c.getRequestProperty("VersionCode"))&&"Android_ilife798_3.1.9".equals(c.getRequestProperty("User-Agent")),"Current device protocol headers");c.disconnect();
        Device stale=new Device("12345678","fixture");stale.running=true;stale.ownStart=System.currentTimeMillis()-30000;
        Device restored=Device.from(stale.json());check(restored.canEnd()&&restored.restored,"Cold process restores a stop-capable hint rather than confirmed running state");
        restored.acceptStatus(99);check(!restored.canEnd()&&restored.ownStart==0&&!restored.restored,"First fresh idle response clears stale cold-start state");
        restored=Device.from(stale.json());restored.acceptStatus(0);check(restored.canEnd()&&!restored.restored,"Cold-start running hardware stays stoppable");
        stale.ownStart=System.currentTimeMillis();restored=Device.from(stale.json());restored.acceptStatus(99);check(restored.canEnd(),"Cold-start preserves short startup status-lag protection");
        final Device cached=Device.from(new JSONObject().put("id","12345678").put("name","宿舍楼 · 一层").put("running",true).put("ownStart",System.currentTimeMillis()-30000));
        runOnMainSync(()->{activity.devices.set(0,cached);activity.showDevices();activity.refresh();});awaitJob();check(!activity.devices.get(0).running,"Refresh copy preserves cold-start reconciliation flag");
        JSONObject release=new JSONObject().put("tag_name","v1.3.0").put("body","fixture release notes").put("assets",new JSONArray().put(new JSONObject().put("name","WaterLite-1.3.0.apk").put("size",100).put("browser_download_url",UpdateClient.PROJECT+"/releases/download/v1.3.0/WaterLite-1.3.0.apk")));
        UpdateClient.Release update=UpdateClient.parse(release);check(update!=null&&update.newerThan("1.2.0"),"Update parser accepts newer stable release with APK");
        check(!update.newerThan("1.3.0")&&!update.newerThan("2.0.0"),"Update comparison rejects same and older releases");
        check(new UpdateClient.Release("v1.10.0","").newerThan("1.9.9"),"Update compares numeric versions rather than text");
        release.put("prerelease",true);check(UpdateClient.parse(release)==null,"Update ignores prereleases");release.put("prerelease",false).put("draft",true);check(UpdateClient.parse(release)==null,"Update ignores drafts");
        release.put("draft",false).put("tag_name","v1.3.0-beta");check(UpdateClient.parse(release)==null,"Update rejects invalid stable version tags");
        release.put("tag_name","v1.3.0").put("assets",new JSONArray());check(UpdateClient.parse(release)==null,"Update ignores releases without an APK");
        runOnMainSync(()->{try{Field pending=MainActivity.class.getDeclaredField("pendingUpdate");pending.setAccessible(true);pending.set(activity,update);Method show=MainActivity.class.getDeclaredMethod("showUpdate");show.setAccessible(true);show.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
        awaitCondition(()->accessibleText("发现新版本 v1.3.0"),4000);Thread.sleep(450);waitForIdleSync();screenshot("qa-update.png");check(accessibleText("发现新版本 v1.3.0")&&accessibleText("fixture release notes")&&accessibleText("GitHub")&&accessibleText("取消"),"Update dialog displays version notes Cancel and GitHub");clickDialog("取消");waitForIdleSync();
        check(update.url.equals(UpdateClient.PROJECT+"/releases/tag/v1.3.0"),"Update link stays on the intended GitHub project release");
    }
    private void verifyPalette(){
        AppPalette palette=activity.palette;
        check(contrast(palette.text,palette.background)>=4.5,"Page text meets WCAG contrast");
        check(contrast(palette.text,palette.surface)>=4.5,"Card text meets WCAG contrast");
        check(contrast(palette.textSecondary,palette.surfaceVariant)>=4.5,"Secondary text meets WCAG contrast");
        check(contrast(palette.onPrimary,palette.primary)>=4.5,"Primary button text meets WCAG contrast");
        check(contrast(palette.onError,palette.error)>=4.5,"Stop button text meets WCAG contrast");
        check(contrast(palette.onPrimaryContainer,palette.primaryContainer)>=4.5,"Tonal action text meets WCAG contrast");
        if(Build.VERSION.SDK_INT>=31){boolean dark=(activity.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;check(palette.primary==android.graphics.Color.parseColor(dark?"#80B4FF":"#0065EB"),"MIUI primary is fixed and independent of wallpaper");}
        runOnMainSync(()->{android.util.TypedValue accent=new android.util.TypedValue();check(activity.getTheme().resolveAttribute(android.R.attr.colorAccent,accent,true)&&accent.data==palette.primary,"Native controls and dialogs share MIUI accent");});
    }
    private static double contrast(int foreground,int background){double a=android.graphics.Color.luminance(foreground),b=android.graphics.Color.luminance(background);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
    private void verifyDisabledAnimations() throws Exception {
        String original=shell("settings get global animator_duration_scale").trim();
        try{
            shell("settings put global animator_duration_scale 0");awaitCondition(()->!android.animation.ValueAnimator.areAnimatorsEnabled(),3000);
            runOnMainSync(()->find(activity.getWindow().getDecorView(),"开始出水",false).performClick());awaitJob();
            check(activity.devices.get(0).canEnd()&&buttonColor("停止出水")==activity.palette.error,"Disabled animations immediately show the correct running control");
            runOnMainSync(()->find(activity.getWindow().getDecorView(),"停止出水",false).performClick());awaitJob();
            check(!activity.devices.get(0).canEnd()&&buttonColor("开始出水")==activity.palette.primary,"Disabled animations immediately restore the idle control");
            runOnMainSync(()->{activity.refresh();android.animation.Animator animation=(android.animation.Animator)objectField("refreshAnimation");check(animation==null||!animation.isRunning(),"Disabled animations never start an infinite refresh spinner");});awaitJob();
            check(!booleanFieldOnMain("refreshing")&&activity.findViewById(104).getRotation()==0,"Refresh completes and restores icon with animations disabled");
        }finally{
            if(original.equals("null")||original.isEmpty())shell("settings delete global animator_duration_scale");
            else if(original.matches("[0-9]+(?:\\.[0-9]+)?"))shell("settings put global animator_duration_scale "+original);
            else throw new AssertionError("Unexpected animator duration setting: "+original);
        }
    }
    private void verifyStableDeviceList() throws Exception {
        runOnMainSync(()->{for(int i=0;i<13;i++){Device device=new Device("900000"+String.format(Locale.US,"%02d",i),"流畅性测试设备 "+(i+1));device.status=99;activity.devices.add(device);}activity.showDevices();});waitForIdleSync();
        final ScrollView[] scroll={null};final Button[] originalButton={null};final int[] position={0};
        runOnMainSync(()->{scroll[0]=(ScrollView)objectField("deviceScroll");originalButton[0]=controlButton("12345678");scroll[0].scrollTo(0,activity.dp(300));});waitForIdleSync();
        runOnMainSync(()->position[0]=scroll[0].getScrollY());check(activity.devices.size()>=14&&position[0]>0,"Large device list scrolls with at least fourteen devices");screenshot("qa-many-devices.png");
        runOnMainSync(()->activity.refresh());awaitJob();
        runOnMainSync(()->{check(objectField("deviceScroll")==scroll[0]&&scroll[0].getScrollY()==position[0],"Refresh retains the device scroll container and position");check(controlButton("12345678")==originalButton[0],"Unchanged refresh retains control button identity");});
        final String pinId="90000002";
        runOnMainSync(()->{View row=find(activity.getWindow().getDecorView(),"流畅性测试设备 3",true);while(row!=null&&!row.getClass().getSimpleName().equals("SwipeRow"))row=(View)row.getParent();((ViewGroup)((ViewGroup)row).getChildAt(0)).getChildAt(0).performClick();});waitForIdleSync();
        runOnMainSync(()->{Device target=null;for(Device device:activity.devices)if(device.id.equals(pinId))target=device;check(target!=null&&target.pinned,"Retained row pin action updates the current refreshed device");check(objectField("deviceScroll")==scroll[0]&&scroll[0].getScrollY()==position[0],"Pinning retains the device scroll container and position");});
    }
    @SuppressWarnings("unchecked") private Button controlButton(String id){return ((Map<String,Button>)objectField("controlButtons")).get(id);}
    private Object objectField(String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private String shell(String command) throws Exception {try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command);java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[1024];int count;while((count=stream.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8");}}
    private void awaitJob() throws Exception {for(int i=0;i<600;i++){boolean[] b={true};runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField("busy");f.setAccessible(true);b[0]=f.getBoolean(activity);}catch(Exception e){throw new RuntimeException(e);}});if(!b[0]){waitForIdleSync();return;}Thread.sleep(50);}throw new AssertionError("Job timeout");}
    private boolean booleanField(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private boolean booleanFieldOnMain(String name){boolean[] result={false};runOnMainSync(()->result[0]=booleanField(name));return result[0];}
    private int buttonColor(String label){int[] color={0};runOnMainSync(()->{android.graphics.drawable.RippleDrawable ripple=(android.graphics.drawable.RippleDrawable)find(activity.getWindow().getDecorView(),label,false).getBackground();color[0]=((android.graphics.drawable.GradientDrawable)ripple.getDrawable(0)).getColor().getDefaultColor();});return color[0];}
    private boolean accessibleText(String label){for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(label))if(n.getText()!=null&&label.contentEquals(n.getText()))return true;}return false;}
    private void returnToActivity() throws Exception {try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand("am start -n com.joe.waterlite/.MainActivity --activity-reorder-to-front");java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){while(stream.read()!=-1){}}awaitCondition(()->booleanField("foreground"),3000);}
    private void background() throws Exception {runOnMainSync(()->activity.moveTaskToBack(true));awaitCondition(()->!booleanField("foreground"),3000);}
    private void backgroundAndReturn() throws Exception {background();returnToActivity();}
    private void awaitCondition(BooleanSupplier condition,long timeout) throws Exception {long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){boolean[] ready={false};runOnMainSync(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(30);}throw new AssertionError("Automatic state transition timeout");}
    private void screenshot(String name) throws Exception {android.graphics.Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}
    private void clickDialog(String label) throws Exception {for(int i=0;i<40;i++){for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(label)){if(n.getText()!=null&&label.contentEquals(n.getText())&&n.isClickable()){n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(100);return;}}}Thread.sleep(50);}throw new AssertionError("Dialog button not found: "+label);}
    private TextView findText(String s){final TextView[] out={null};runOnMainSync(()->out[0]=find(activity.getWindow().getDecorView(),s,false));return out[0];}
    private boolean contains(String s){final TextView[] out={null};runOnMainSync(()->out[0]=find(activity.getWindow().getDecorView(),s,true));return out[0]!=null;}
    private TextView find(View v,String s,boolean partial){if(v instanceof TextView){String text=((TextView)v).getText().toString();if(partial?text.contains(s):text.equals(s))return (TextView)v;}if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){TextView result=find(group.getChildAt(i),s,partial);if(result!=null)return result;}}return null;}
    private static class MockApi extends IlifeApi {
        volatile int accountCode=0,accountReads=0,machineStatus=99,lagIdleReads=0,statusReads=0,startCode=0,stopCode=0;
        volatile boolean transportFailure=false,statusFailure=false;boolean correctPlatform=true;int starts,ends;
        volatile CountDownLatch statusGate,statusEntered;
        @Override Result request(String method,String path,JSONObject body,String token,String platform) throws Exception {
            if(!path.equals("/acc/login/code")&&!platform.equals("1,1"))correctPlatform=false;
            if(transportFailure)throw new java.io.IOException("fixture network failure");
            JSONObject response=new JSONObject().put("code",0).put("msg","");
            if(path.equals("/acc/view-info")){accountReads++;return new Result(response.put("code",accountCode).put("data",new JSONObject().put("id","fixture")));}
            if(path.equals("/ui/app/master"))response.put("data",new JSONObject().put("favos",new JSONArray().put(new JSONObject().put("id","12345678").put("name","宿舍楼 · 一层")).put(new JSONObject().put("id","87654321").put("name","图书馆 · 二层"))));
            else if(path.startsWith("/ui/app/dev/status")){
                int state=99;if(path.contains("12345678")){statusReads++;state=machineStatus;CountDownLatch gate=statusGate;if(gate!=null){statusEntered.countDown();if(!gate.await(15,TimeUnit.SECONDS))throw new java.io.IOException("fixture slow status timeout");}if(statusFailure)throw new java.io.IOException("fixture status failure");if(lagIdleReads>0){lagIdleReads--;state=99;}}
                response.put("data",new JSONObject().put("device",new JSONObject().put("name","fixture device").put("gene",new JSONObject().put("status",state))));
            }
            else if(path.equals("/acc/wallet/owner"))response.put("data",new JSONObject().put("aw",new JSONObject().put("ep",new JSONObject().put("id","test").put("name","测试钱包")).put("total",12.34)));
            else if(path.startsWith("/acc/login?sign="))response.put("data",new JSONObject().put("al",new JSONObject().put("token","new-fixture-token")));
            else if(path.startsWith("/dev/start")){starts++;response.put("code",startCode);if(startCode==0)machineStatus=0;}
            else if(path.startsWith("/dev/end")){ends++;response.put("code",stopCode);if(stopCode==0)machineStatus=99;}
            return new Result(response);
        }
        @Override android.graphics.Bitmap captcha(String key){return android.graphics.Bitmap.createBitmap(160,60,android.graphics.Bitmap.Config.ARGB_8888);}
    }
}
