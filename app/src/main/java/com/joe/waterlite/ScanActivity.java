package com.joe.waterlite;

import android.app.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.hardware.Camera;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public class ScanActivity extends Activity implements SurfaceHolder.Callback,Camera.PreviewCallback {
    private Camera camera;
    private SurfaceView preview;
    private TextView tip;
    private AppPalette palette;
    private boolean resumed,ready,found;
    private volatile boolean decoding;
    private long lastFrame;
    private int cameraId,epoch,previewWidth,previewHeight;
    private byte[] luminance;
    private final MultiFormatReader reader=new MultiFormatReader();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle b){
        super.onCreate(b);palette=AppPalette.from(this);getWindow().setStatusBarColor(palette.background);getWindow().setNavigationBarColor(palette.background);getWindow().getDecorView().setSystemUiVisibility(Color.luminance(palette.background)>.5?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,true);reader.setHints(hints);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(palette.background);root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in.consumeSystemWindowInsets();});
        preview=new SurfaceView(this);preview.getHolder().addCallback(this);root.addView(preview);
        FrameView guide=new FrameView(this);guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(guide);
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(24),dp(20),dp(24),dp(24));top.setBackgroundColor(palette.surface);
        Button back=button("返回");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(88),dp(48)));
        tip=new TextView(this);tip.setText("扫描饮水机机身二维码\n识别后可确认设备名称");tip.setTextColor(palette.text);tip.setTextSize(20);tip.setPadding(0,dp(20),0,0);top.addView(tip);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        Button manual=button("手动输入设备编号");manual.setOnClickListener(v->manual());FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(56),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.bottomMargin=dp(24);root.addView(manual,lp);setContentView(root);root.requestApplyInsets();
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},20);
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(palette.onPrimaryContainer);b.setPadding(dp(20),0,dp(20),0);GradientDrawable fill=new GradientDrawable();fill.setColor(palette.primaryContainer);fill.setCornerRadius(dp(28));b.setBackground(new RippleDrawable(ColorStateList.valueOf((palette.onPrimaryContainer&0x00ffffff)|0x22000000),fill,null));return b;}
    private void manual(){EditText e=new EditText(this);e.setHint("设备编号或二维码内容");e.setSingleLine(true);e.setTextColor(palette.text);e.setHintTextColor(palette.textSecondary);e.setHighlightColor((palette.primary&0x00ffffff)|0x55000000);e.setBackgroundTintList(ColorStateList.valueOf(palette.primary));new AlertDialog.Builder(this).setTitle("手动添加").setView(e).setNegativeButton("取消",null).setPositiveButton("继续",(a,w)->deliver(e.getText().toString())).show();}
    private void deliver(String code){if(found)return;found=true;Intent i=new Intent();i.putExtra("code",code);setResult(RESULT_OK,i);finish();}
    private void start(){
        if(camera!=null||!resumed||!ready||found||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            cameraId=-1;Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){cameraId=i;break;}}if(cameraId<0)throw new Exception("no back camera");
            camera=Camera.open(cameraId);Camera.Parameters p=camera.getParameters();Camera.Size chosen=null;for(Camera.Size size:p.getSupportedPreviewSizes()){if(size.width<=1280&&(chosen==null||size.width>chosen.width))chosen=size;}if(chosen!=null)p.setPreviewSize(chosen.width,chosen.height);
            p.setPreviewFormat(ImageFormat.NV21);List<String> focus=p.getSupportedFocusModes();if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);camera.setParameters(p);Camera.Size active=camera.getParameters().getPreviewSize();previewWidth=active.width;previewHeight=active.height;lastFrame=0;
            Camera.getCameraInfo(cameraId,info);int rotation=getWindowManager().getDefaultDisplay().getRotation();int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;camera.setDisplayOrientation((info.orientation-degrees+360)%360);
            camera.setPreviewDisplay(preview.getHolder());camera.setPreviewCallback(this);camera.startPreview();
        }catch(Exception e){stop();tip.setText("相机暂时无法打开\n请检查权限，或手动输入设备编号。");}
    }
    private void stop(){epoch++;Camera c=camera;camera=null;if(c!=null){try{c.setPreviewCallback(null);c.stopPreview();}catch(Exception ignored){}c.release();}}
    @Override protected void onResume(){super.onResume();resumed=true;start();}
    @Override protected void onPause(){resumed=false;stop();super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==20&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)start();else tip.setText("未获得相机权限\n可以手动输入设备编号。");}
    public void surfaceCreated(SurfaceHolder h){ready=true;start();}
    public void surfaceChanged(SurfaceHolder h,int f,int w,int t){}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;stop();}
    public void onPreviewFrame(byte[] data,Camera c){
        if(c!=camera||found||decoding||!resumed||worker.isShutdown()||SystemClock.elapsedRealtime()-lastFrame<300)return;final int width=previewWidth,height=previewHeight,length=width*height;if(data==null||length<=0||data.length<length)return;
        if(luminance==null||luminance.length!=length)luminance=new byte[length];System.arraycopy(data,0,luminance,0,length);final byte[] frame=luminance;final int ticket=epoch;lastFrame=SystemClock.elapsedRealtime();decoding=true;
        worker.execute(()->{String code=null;try{if(Thread.currentThread().isInterrupted())return;PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(frame,width,height,0,0,width,height,false);try{code=reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source))).getText();}catch(NotFoundException e){reader.reset();code=reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source.invert()))).getText();}}catch(Exception ignored){}finally{reader.reset();decoding=false;}final String result=code;if(result!=null)runOnUiThread(()->{if(ticket==epoch&&resumed&&!isFinishing()&&!isDestroyed())deliver(result);});});
    }
    private final class FrameView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FrameView(Context c){super(c);paint.setColor(palette.primary);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));}
        @Override protected void onDraw(Canvas c){float side=Math.min(getWidth(),getHeight())*.68f,left=(getWidth()-side)/2,top=(getHeight()-side)/2;c.drawRoundRect(left,top,left+side,top+side,dp(24),dp(24),paint);}
    }
}
