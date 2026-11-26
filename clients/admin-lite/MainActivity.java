package app.lensdrop.adminlite;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.webkit.WebResourceRequest;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;

/** A deliberately narrow, cloud-hosted pairing console. No Admin Key is stored natively. */
public final class MainActivity extends Activity {
    private LinearLayout root;
    private WebView web;
    private String trustedOrigin;
    private static final String PREF="lensdrop_admin_lite_url";
    private static final String DEFAULT_URL="https://honeydew-crocodile-457025.hostingersite.com";
    private TextView status;

    private TextView text(String value, int sp, boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(Color.rgb(28,65,50));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,12,0,12);return t;
    }
    private LinearLayout base() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(241,247,243));
        root.setPadding(20,20,20,20);setContentView(root);return root;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        showSetup();
    }
    private String originOf(String raw){
        String input=raw==null?"":raw.trim();Uri u=Uri.parse(input);
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getPort()>0&&u.getPort()!=443)
            throw new IllegalArgumentException("Enter an HTTPS Cloud URL (no username/password or nonstandard port).");
        if(u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Use only the root Cloud URL without query or fragment.");
        return "https://"+u.getHost().toLowerCase(java.util.Locale.ROOT);
    }
    private void showSetup(){
        if(web!=null){web.destroy();web=null;}
        base();root.addView(text("LensDrop · Admin Lite",24,true));root.addView(text("Photographer pairing, approval and device control. No printing or event-photo deletion.",14,false));
        root.addView(text("Cloud HTTPS address",14,true));
        EditText url=new EditText(this);url.setSingleLine(true);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(getPreferences(0).getString(PREF,DEFAULT_URL));url.setSelectAllOnFocus(true);root.addView(url);
        status=text("Only enter your own trusted Hostinger Cloud domain. Admin keys are entered inside the secure web page and are never saved by this app.",12,false);root.addView(status);
        Button open=new Button(this);open.setText("OPEN ADMIN PAIRING CONSOLE");root.addView(open);
        open.setOnClickListener(v->{try{trustedOrigin=originOf(url.getText().toString());getPreferences(0).edit().putString(PREF,trustedOrigin).apply();showWeb();}
            catch(Exception e){status.setText("Cannot open Cloud: "+e.getMessage());}});
    }
    private void showWeb(){
        base();LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);
        TextView domain=text("Secure: "+Uri.parse(trustedOrigin).getHost(),11,true);toolbar.addView(domain,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button change=new Button(this);change.setText("CHANGE URL");change.setTextSize(10);toolbar.addView(change);change.setOnClickListener(v->showSetup());root.addView(toolbar);
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);s.setMediaPlaybackRequiresUserGesture(true);
        // Pairing approval uses JavaScript confirm/prompt. A WebChromeClient is
        // mandatory: without it a WebView may silently suppress those actions.
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setTitle("LensDrop confirmation").setMessage(message)
                    .setPositiveButton("Confirm",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();return true;
            }
            @Override public boolean onJsPrompt(WebView view,String url,String message,String defaultValue,JsPromptResult result){
                EditText code=new EditText(MainActivity.this);code.setSingleLine(true);code.setInputType(InputType.TYPE_CLASS_NUMBER);
                code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
                new AlertDialog.Builder(MainActivity.this).setTitle("Admin approval code").setMessage(message).setView(code)
                    .setPositiveButton("Submit",(d,w)->result.confirm(code.getText().toString()))
                    .setNegativeButton("Cancel",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req){return !approved(req.getUrl());}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){handler.cancel();}
        });
        root.addView(web,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        web.loadUrl(trustedOrigin+"/admin-lite");
    }
    private boolean approved(Uri u){
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null)return false;
        return ("https://"+u.getHost().toLowerCase(java.util.Locale.ROOT)).equals(trustedOrigin) && (u.getPort()==-1||u.getPort()==443);
    }
    @Override public void onBackPressed(){if(web!=null){showSetup();}else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.clearHistory();web.clearCache(true);web.destroy();web=null;}super.onDestroy();}
}
