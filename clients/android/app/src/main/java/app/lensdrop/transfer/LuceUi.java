package app.lensdrop.transfer;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

/** Lightweight native view styling. No WebView, image assets or background service. */
final class LuceUi {
    static final int INK=Color.rgb(23,48,61), MUTED=Color.rgb(83,104,115), ACCENT=Color.rgb(20,95,86);
    static final int BACK=Color.rgb(244,247,249), LINE=Color.rgb(216,226,231), SOFT=Color.rgb(232,243,240);
    static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Activity a,int color,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,14));if(border!=0)d.setStroke(dp(a,1),border);return d;}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout screen(Activity a,String title,String subtitle,boolean back){
        if(Build.VERSION.SDK_INT>=30)a.getWindow().setDecorFitsSystemWindows(false);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column(a);root.setBackgroundColor(BACK);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout body=column(a);int p=dp(a,18);body.setPadding(p,p,p,p);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));a.setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{int top,bottom,left,right;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;}
            else{top=i.getSystemWindowInsetTop();bottom=i.getSystemWindowInsetBottom();left=i.getSystemWindowInsetLeft();right=i.getSystemWindowInsetRight();}
            root.setPadding(left,top,right,bottom);return i;});
        root.requestApplyInsets();
        if(back){Button b=action(a,body,"Back",false,a::finish);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);}
        label(a,body,title,28,true);label(a,body,subtitle,14,false);return body;
    }
    static LinearLayout card(Activity a,LinearLayout parent,String title,String note){
        LinearLayout box=column(a);box.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,14));box.setBackground(shape(a,Color.WHITE,LINE));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,14),0,0);parent.addView(box,lp);
        if(title!=null&&!title.isEmpty())label(a,box,title,18,true);if(note!=null&&!note.isEmpty())label(a,box,note,14,false);return box;
    }
    static TextView label(Activity a,LinearLayout parent,String text,int size,boolean bold){TextView v=new TextView(a);v.setText(text);v.setTextSize(size);v.setTextColor(bold?INK:MUTED);v.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);v.setPadding(0,dp(a,4),0,dp(a,6));v.setLineSpacing(dp(a,2),1f);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    static TextView notice(Activity a,LinearLayout parent,String text){TextView v=label(a,parent,text,15,false);v.setTextColor(INK);v.setPadding(dp(a,12),dp(a,11),dp(a,12),dp(a,11));v.setBackground(shape(a,SOFT,0));return v;}
    static Button action(Activity a,LinearLayout parent,String text,boolean primary,Runnable task){
        Button b=new Button(a);b.setAllCaps(false);b.setText(text);b.setTextSize(16);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(a,52));b.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,10));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,primary?Color.WHITE:ACCENT}));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},shape(a,Color.rgb(232,237,239),LINE));states.addState(new int[]{},new RippleDrawable(ColorStateList.valueOf(Color.rgb(190,218,212)),shape(a,primary?ACCENT:SOFT,primary?0:LINE),null));b.setBackground(states);b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,7),0,dp(a,3));parent.addView(b,lp);b.setOnClickListener(v->task.run());return b;
    }
    static EditText field(Activity a,LinearLayout p,String hint,String value,boolean secret){
        EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setSingleLine(true);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setMinHeight(dp(a,52));e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));e.setBackground(shape(a,Color.WHITE,LINE));e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        if(secret)e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);else e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,7),0,dp(a,5));p.addView(e,lp);return e;
    }
    static CheckBox check(Activity a,LinearLayout p,String text,boolean checked){CheckBox c=new CheckBox(a);c.setText(text);c.setTextSize(15);c.setTextColor(INK);c.setButtonTintList(ColorStateList.valueOf(ACCENT));c.setPadding(0,dp(a,7),0,dp(a,7));c.setChecked(checked);p.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
    static LinearLayout disclosure(Activity a,LinearLayout p,String title){LinearLayout box=column(a);Button toggle=action(a,p,title+" +",false,()->{});box.setVisibility(View.GONE);p.addView(box);toggle.setOnClickListener(v->{boolean open=box.getVisibility()!=View.VISIBLE;box.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(title+(open?" -":" +"));});return box;}
    static String version(Activity a){try{android.content.pm.PackageInfo x=a.getPackageManager().getPackageInfo(a.getPackageName(),0);return x.versionName+" / code "+x.getLongVersionCode();}catch(Exception e){return "Version unavailable";}}
    private LuceUi(){}
}
