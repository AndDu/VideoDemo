package com.example.administrator.videodemo;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private MyVideoView videoview;
    private RelativeLayout vvParent;

    private View fl_controller;
    private View ll_title;
    private View ll_play;
    private View iv_return;
    private View iv_fullgreen;
    private float rate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoview = findViewById(R.id.videoview);
        vvParent = findViewById(R.id.rl_parent);
        fl_controller = findViewById(R.id.fl_controller);
        ll_title = findViewById(R.id.ll_title);
        ll_play = findViewById(R.id.ll_play);
        iv_return = findViewById(R.id.iv_return);
        iv_fullgreen = findViewById(R.id.iv_fullgreen);
        videoview.setVideoPath("http://www.i5campus.com:9037/HAPPYOUVService/uploadFile/video/video.mp4");
//        videoview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//            @Override
//            public void onPrepared(MediaPlayer mp) {
//                mp.start();
//                rate = mp.getVideoWidth()*1f / mp.getVideoHeight();
//            }
//        });
        fl_controller.setOnClickListener(this);
        iv_return.setOnClickListener(this);
        iv_fullgreen.setOnClickListener(this);


        Test test = new Test();
        test.initMediaDecode();
    }

    @Override
    public void onClick(View v) {
        if (v == fl_controller) {
            ll_title.setVisibility(ll_title.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            ll_play.setVisibility(ll_play.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        } else if (v == iv_fullgreen) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e(": ","onConfigurationChanged" );
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

            int height = DensityUtil.dip2px(this, 200);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            int screenWidth = Math.min(ScreenUtils.getScreenWidth(this), ScreenUtils.getScreenHeight(this));
            setVvParent(screenWidth, height);
            setFlController(screenWidth, height);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

        } else {

            int screenHeight = Math.min(ScreenUtils.getScreenHeight(this), ScreenUtils.getScreenWidth(this));
            int screenwidth = Math.max(ScreenUtils.getScreenHeight(this), ScreenUtils.getScreenWidth(this));
            setVvParent((int) (screenHeight * rate), screenHeight);
            setFlController(screenwidth, screenHeight);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        }
    }

    void setVvParent(int width, int height) {
        ViewGroup.LayoutParams lp = vvParent.getLayoutParams();
        lp.height = height;
        lp.width = width;
        vvParent.setLayoutParams(lp);
    }

    void setFlController(int width, int height) {
        ViewGroup.LayoutParams lp = fl_controller.getLayoutParams();
        lp.width = width;
        lp.height = height;
        fl_controller.setLayoutParams(lp);
    }
}
