package com.mifeng.us.vitamiodemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;

import io.vov.vitamio.LibsChecker;
import io.vov.vitamio.MediaPlayer;
import io.vov.vitamio.widget.MediaController;
import io.vov.vitamio.widget.VideoView;

public class PlayerActivity extends AppCompatActivity {
    private static final int SEEKBAR_WHAT = 200; //seekbar 更新的what值
    private static final int BOTTOM_GONE_WHAT = 201;
    private static final int LIGHT_BAR_GONE_WHAT = 202;
    private static final int SOUND_BAR_GONE_WHAT = 203;
    private static final int PLAY_STATUS_PLAY = 100; //如果是play状态的话 图标应该是 双竖线
    private static final int PLAY_STATUS_PAUSE = 101; //如果是暂停状态 图标应该是 三角
    private int status_play = PLAY_STATUS_PLAY;
    private VideoView mVideoView;
    private String path;
    private ImageView imageView;
    private LinearLayout relativeLayout;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what==SEEKBAR_WHAT){
                //在这给mHandler自己发消息，然后循环处理
                long currentPosition = mVideoView.getCurrentPosition();//获取当前播放位置
                float v = currentPosition / videoScale; //获取当前seekbar真实的位置
                seekBar.setProgress((int)v);
                mHandler.sendEmptyMessageDelayed(SEEKBAR_WHAT,2000);
            }else if (msg.what==BOTTOM_GONE_WHAT){
                relativeLayout.setVisibility(View.GONE);
            }else if (msg.what == LIGHT_BAR_GONE_WHAT) {
                fl_light_bar.setVisibility(View.GONE);
            } else if (msg.what == SOUND_BAR_GONE_WHAT) {
                fl_sound_bar.setVisibility(View.GONE);
            }
        }
    };
    private ImageView img_player;
    private SeekBar seekBar;
    private long videoLength;//视频长度
    private float videoScale;//视频长度和seekbar进度的比例

    private float preY;
    private float lastY;
    private int defaultScreenMode;
    private int defaultscreenBrightness;
    private float lightScale;//亮度比例值
    private float newLight;
    private TextView lightBar;
    private FrameLayout fl;

    private float newSound;//调整之后的音量

    private int videoViewStartY;
    private int videoViewEndY;
    private int currentSystemSoundsValue;
    private AudioManager mAudioManager;
    private ImageView iv_sound;
    private SettingsContentObserver mSettingsContentObserver;
    private TextView soundBar;
    private FrameLayout fl_sound_bar;
    private Double soundScale;
    private int maxSound;
    private FrameLayout fl_light_bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //初始化vitamio
        if (!LibsChecker.checkVitamioLibs(this))
            return;
        setContentView(R.layout.activity_player);
        initIntent();
        findid();
        initVideoViewTouchLisener();
        initImg();
        initEvent();
        int currentSoundValue = VideoApplication.getApp().getCurrentSoundValue();
        if (currentSoundValue == 0) {
            //说明没有播放过
            //获取系统媒体音量
            getSystemMediaSoundValue(0);
        } else {
            //说明播放过，把当前的音量设置为上次设置的音量
            setCurrentMediaSoundValue(currentSoundValue);
        }

        startPlay();
        //注册音量监听
        registerVolumeChangeReceiver();

        //存储系统初始亮度
        try {
            getSystemLightValue();
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
    }
    //获得系统音量值，传入值为0时，会更改系统音量值
    /*
    参数 count的意思是是否记录当前的音量值  count为零的话说明记录，并设置成进入app之前的系统音量
    如果count不为零 就不把他当做之前的系统音量 ，就只是临时获取的音量
     */
    private int getSystemMediaSoundValue(int count) {
        //音频管理者
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 系统的是：0到Max，Max不确定，根据手机而定
        maxSound = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        //媒体系统音量值
        currentSystemSoundsValue = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        newSound=currentSystemSoundsValue;
        //让application全局去管理音量
        if (count == 0) { //传入0时拿到的是系统的亮度，给系统亮度赋值
            VideoApplication.getApp().setSystemSoundValue(currentSystemSoundsValue);
            //每滑动一个像素 要更改的音量值
            soundScale = maxSound *1.0000d/ (getResources().getDimensionPixelSize(R.dimen.videoview_height_6));
        } else {
            VideoApplication.getApp().setCurrentSoundValue(currentSystemSoundsValue);
        }
        //否则拿到的是当前的亮度
        return currentSystemSoundsValue;
    }

    //设置当前音量为xx值
    private void setCurrentMediaSoundValue(int value) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        //设置音量当前值
        /**
         * 参数1：当前要改变的音量的类型
         * 参数2:的范围是0-max(19)
         *
         */
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);

    }

    //降低当前音量
    private void downMediaSound() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
    }

    //增加当前音量
    private void upMediaSound() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
    }

    private void getSystemLightValue() throws Settings.SettingNotFoundException {
        /* 获得当前屏幕亮度的模式
         * SCREEN_BRIGHTNESS_MODE_AUTOMATIC=1 为自动调节屏幕亮度
         * SCREEN_BRIGHTNESS_MODE_MANUAL=0 为手动调节屏幕亮度
         */
        defaultScreenMode= AjustSystemLightUtil.getSystemLightMode(this);
        // 获得当前屏幕亮度值 0--255
        defaultscreenBrightness = AjustSystemLightUtil.getSystemLightValue(this);
        newLight = defaultscreenBrightness;//为了下文中设置亮度用的变量
        //计算手指滑动改变亮度的比例值
        lightScale = 255.0f / getResources().getDimensionPixelSize(R.dimen.videoview_height);
        setLightBarHeight(defaultscreenBrightness);
    }

    private void initEvent() {
        //设置自定义进度条的监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress, boolean fromUser) {
                //当进度发生变化时的监听

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //当开始触摸拖拽时的监听
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //停止触摸时候的监听


                int progress = seekBar.getProgress();
                //通过seekbar拿到当前的进度
                final float v = progress * videoScale;//当前的播放进度
                mVideoView.seekTo((long) v);   //把视频定位到这个位置
            }
        });
        //设置videoView自带的 拖拽进度监听
    }
    @SuppressLint("ClickableViewAccessibility")
    private void initVideoViewTouchLisener() {
        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    //按下的时候，让rl显示出来
                    if (relativeLayout.getVisibility() == View.GONE) {
                        relativeLayout.setVisibility(View.VISIBLE);
                        //清空所有消息
                        mHandler.removeMessages(BOTTOM_GONE_WHAT);
                        mHandler.sendEmptyMessageDelayed(BOTTOM_GONE_WHAT,3000);
                    } else if (relativeLayout.getVisibility() == View.VISIBLE) {
                        relativeLayout.setVisibility(View.GONE);
                    }
                }
                return false;
            }
        });
    }
    private void initImg() {
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //判断当前手机的方向是什么方向
                //如果是水平的
                if (PlayerActivity.this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    //设置手机方向为竖直方向
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    //把titlebar显示出来
                    //titlebar.setVisibility(View.VISIBLE);
                    //切换成垂直方向时，需要重新设置他的宽高
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mVideoView.getLayoutParams();
                    layoutParams.width= ScreenUtil.getScreenWidth(PlayerActivity.this);
                    layoutParams.height=getResources().getDimensionPixelSize(R.dimen.videoview_height);
                    mVideoView.setLayoutParams(layoutParams);
                    imageView.setImageResource(R.mipmap.full_screen);

                } else {
                    //如果当前手机是垂直方向的话，就置为水平
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    //同样把titlebar去掉
                    //titlebar.setVisibility(View.GONE);
                    //切换成水平方向时，需要重新设置他的宽高

                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

                    mVideoView.setLayoutParams(lp);
                    imageView.setImageResource(R.mipmap.full_screen_no);
                }
            }
        });

        img_player.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //如果现在的状态是正在播放
                if (status_play == PLAY_STATUS_PLAY && mVideoView.isPlaying()) {
                    //如果是播放状态，这个时候点击暂停 ，这个时候需要三角图标 提示用户现在是暂停状态
                    //暂停的逻辑
                    mVideoView.pause();
                    mHandler.removeMessages(SEEKBAR_WHAT);
                    img_player.setImageResource(R.mipmap.play);
                    status_play = PLAY_STATUS_PAUSE;

                } else if (status_play == PLAY_STATUS_PAUSE) {
                    mVideoView.start();
                    mHandler.sendEmptyMessage(SEEKBAR_WHAT);
                    img_player.setImageResource(R.mipmap.pause);
                    status_play = PLAY_STATUS_PLAY;
                }
            }
        });
    }

    private void initIntent() {
        Intent intent = getIntent();
        path = intent.getStringExtra("path");
    }

    //播放视频
    private void startPlay() {
        //设置视频的路径 或者网址

        //播放网络视频使用    setVideoURI
        mVideoView.setVideoURI(Uri.parse("https://media.w3.org/2010/05/sintel/trailer.mp4"));
        //mVideoView.setVideoPath(path);
        //设置mediacontroller
        //mVideoView.setMediaController(new MediaController(this));
        //开始请求数据（视频数据）
        mVideoView.requestFocus();
        //添加准备播放的监听

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                // optional need Vitamio 4.0
                mediaPlayer.setPlaybackSpeed(1.0f);  //最大别超过2倍
                //拿到视频的总长度
                videoLength = mVideoView.getDuration();
                //得到进度条和视频长度的比例
                videoScale = videoLength/100;   //因为seekbar的总长度为100
                mHandler.sendEmptyMessage(SEEKBAR_WHAT);
            }
        });

    }

    private void findid() {
        mVideoView = findViewById(R.id.videoview);
        imageView = findViewById(R.id.imageview);
        relativeLayout = findViewById(R.id.layout);
        img_player = findViewById(R.id.img_player);
        seekBar = findViewById(R.id.seekBar);
        lightBar=findViewById(R.id.tx_light);
        fl = findViewById(R.id.fl);
        fl_light_bar = findViewById(R.id.fl_light_bar);
        fl_light_bar.setVisibility(View.GONE);
        //管理音量条变化的bar
        fl_sound_bar = (FrameLayout) findViewById(R.id.fl_sound_bar);
        fl_sound_bar.setVisibility(View.GONE);
        soundBar = (TextView) findViewById(R.id.tv_sound);
    }
    private void setLightBarHeight(int value) {
        //设置亮度条的默认亮度
        //1、先拿到每px代表的亮度
        float v = 255.0f / getResources().getDimensionPixelSize(R.dimen.full_light_height);
        //2、当前亮度值为传入的value 参数
        float defaultLightHeight = value / v;  //得到默认的像素高度
        resetLightBarHeight((int) defaultLightHeight);
    }
    private void setSoundBarHeight(int value) {
        //设置亮度条的默认亮度
        //1、先拿到每px代表的亮度   （在java代码中，所有的尺寸一般都是 px  720p=宽720*高1280     1080p=1080*1920）
        float v = maxSound / getResources().getDimensionPixelSize(R.dimen.full_light_height);

        double scale = value * 1.000d / maxSound;

        //2、当前亮度值为传入的value 参数
        double soundBarHeight = scale * getResources().getDimensionPixelSize(R.dimen.full_light_height);//得到默认的像素高度
        resetSoundBarHeight((int) soundBarHeight);
    }
    //重新设置lightbar的高度
    private void resetLightBarHeight(int defaultLightHeight) {
        ViewGroup.LayoutParams layoutParams = lightBar.getLayoutParams();
        layoutParams.height= defaultLightHeight;
        lightBar.setLayoutParams(layoutParams);
    }
    //重新设置soundbar的高度
    private void resetSoundBarHeight(int height) {
        ViewGroup.LayoutParams layoutParams = soundBar.getLayoutParams();
        layoutParams.height = height;
        soundBar.setLayoutParams(layoutParams);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        //1、拿到屏幕的宽度，目的是为了区分 亮度调整还是音量调整
        int screenWidth = ScreenUtil.getScreenWidth(PlayerActivity.this);
        //2、如果是竖屏的话
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            int[] framelayoutLocation = new int[2];
            fl.getLocationOnScreen(framelayoutLocation);
            videoViewStartY = framelayoutLocation[1];//这个数组第一个元素就是x轴坐标，第二个元素就是Y轴坐标
            videoViewEndY = videoViewStartY + getResources().getDimensionPixelSize(R.dimen.videoview_height);

        }
        //3、如果是横屏的话
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            videoViewStartY = 0;
            videoViewEndY = screenWidth;
        }

        //4、判断当前手指按下x轴坐标，也是为了判断是否是在调整亮度
        float x = event.getX();
        //5、判断手势操作
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            preY = event.getY();  //按下的时候拿到按下的手指坐标
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            lastY = event.getY(); //拿到滑动时的坐标
            float v = lastY - preY;  //算出来当前Y轴方向 滑动的值（这个值分正负，正值表示手指往下滑，说明是要降低亮度，反之就是增加亮度）

            //x>screenWidth是为了判断是否是在屏幕的右侧滑动，是的话 证明是在调整连固定
            // Math.abs(v)得到 y轴方向滑动距离的绝对值，为的是只让用户在手指滑动的时候才显示 亮度条（用户体验）
            if (x > screenWidth / 2 && Math.abs(v) > getResources().getDimensionPixelSize(R.dimen.mindistance)) {
                //属于调节亮度
                //先清空发送控制亮度条的消息
                mHandler.removeMessages(LIGHT_BAR_GONE_WHAT);
                //再显示
                fl_light_bar.setVisibility(View.VISIBLE);

            }
            //显示音量条的逻辑
            if (x < screenWidth / 2 && Math.abs(v) > getResources().getDimensionPixelSize(R.dimen.mindistance)) {

                //先清空发送控制音量条的消息
                mHandler.removeMessages(SOUND_BAR_GONE_WHAT);
                //显示音量条
                fl_sound_bar.setVisibility(View.VISIBLE);
            }
            if (x > screenWidth / 2) {
                //说明是在屏幕的右边，属于亮度调节
                //要增加（减少的亮度值）
                float dY;
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    //如果按下的位置和滑动的位置，出了videoview的范围值的话，就让他成为这个临界值
                    if (preY < videoViewStartY) {
                        preY = videoViewStartY;
                    }
                    if (lastY < videoViewStartY) {
                        lastY = videoViewStartY;
                    }
                    if (preY > videoViewEndY) {
                        preY = videoViewEndY;
                    }
                    if (lastY > videoViewEndY) {
                        lastY = videoViewEndY;
                    }
                }
                //得到调整的亮度差值
                dY = (lastY - preY) * lightScale; //这个是要调整的亮度 是分正负
                //根据亮度差值，得到当前最新的亮度
                newLight = newLight - dY; //为什么是减去呢？因为dy如果是负数，说明我们是在增加亮度，减去一个负数就是


                //加上这个数 所以说是减法
                //调节系统亮度的范围是0-255
                if (newLight > 255) {
                    newLight = 255;
                } else if (newLight < 0) {
                    newLight = 0;
                }
                //设置好亮度值之后，就可以去设置系统的亮度了

                setLightBarHeight((int) newLight);

                try {
                    //设置系统的亮度值
                    AjustSystemLightUtil.setSystemLight(this, (int) newLight);
                } catch (Settings.SettingNotFoundException e) {
                    e.printStackTrace();
                }
            }
            //真正控制音量条的逻辑
            if (x < screenWidth / 2) {
                //说明是要做音量调节
                //说明是在屏幕的右边，属于音量调节
                //要增加（减少的音量值）
                float dY;
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    //如果按下的位置和滑动的位置，出了videoview的范围值的话，就让他成为这个临界值
                    if (preY < videoViewStartY) {
                        preY = videoViewStartY;
                    }
                    if (lastY < videoViewStartY) {
                        lastY = videoViewStartY;
                    }
                    if (preY > videoViewEndY) {
                        preY = videoViewEndY;
                    }
                    if (lastY > videoViewEndY) {
                        lastY = videoViewEndY;
                    }
                }
                //得到调整的亮度差值
                dY = lastY - preY; //这个是要调整的亮度 是分正负
                int soundY = (int) (dY * soundScale); //这个是要调整的亮度 是分正负



                //根据亮度差值，得到当前最新的亮度
                newSound = newSound - soundY; //为什么是减去呢？因为dy如果是负数，说明我们是在增加亮度，减去一个负数就是

                //调节系统亮度的范围是0-255
                if (newSound > maxSound) {
                    newSound = maxSound;
                } else if (newSound < 0) {
                    newSound = 0;
                }
                setSoundBarHeight((int) newSound);
                //把最终的音量值 设置给当前系统
                setCurrentMediaSoundValue((int) newSound);
            }
            preY = lastY;  //最终让之前按下的坐标等于滑动完后的坐标（为了一个良好的用户体验）否则会立即到达255或者0的值，用户体验不好
        }
        //当手指抬起的时候，需要3秒后消失这个条
        if (event.getAction() == MotionEvent.ACTION_UP) {
            //抬起时，三秒后让fl_light_bar消失
            if (x > screenWidth / 2) {
                mHandler.sendEmptyMessageDelayed(LIGHT_BAR_GONE_WHAT, 1500);
            }
            if (x < screenWidth / 2) {
                mHandler.sendEmptyMessageDelayed(SOUND_BAR_GONE_WHAT, 1500);
            }
        }


        return super.onTouchEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        //因为 这里仅仅 做视频的亮度调节 ，不能影响手机的亮度，所以在不看视频的时候 亮度应该回归到最初的位置
        AjustSystemLightUtil.resetSystemLight(this,defaultScreenMode,defaultscreenBrightness);
    }
    private void registerVolumeChangeReceiver() {
        mSettingsContentObserver = new SettingsContentObserver(this, new Handler());
        getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver);
    }

    private void unregisterVolumeChangeReceiver() {
        getContentResolver().unregisterContentObserver(mSettingsContentObserver);
    }

    public class SettingsContentObserver extends ContentObserver {
        Context context;

        public SettingsContentObserver(Context c, Handler handler) {
            super(handler);
            context = c;
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            VideoApplication.getApp().setCurrentSoundValue(currentVolume);
            if (currentVolume == 0) {
                //是静音
                //iv_sound.setBackgroundResource(R.drawable.nosound);
            } else {
                //非静音
                //iv_sound.setBackgroundResource(R.drawable.sound);
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //恢复系统的媒体音量
        setCurrentMediaSoundValue(VideoApplication.getApp().getSystemSoundValue());
    }
}
