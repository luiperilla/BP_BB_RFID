package com.agp.uhf.activity;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTabHost;

import com.agp.uhf.R;
import com.agp.uhf.fragment.BlockPermalockFragment;
import com.agp.uhf.fragment.BlockWriteFragment;
import com.agp.uhf.fragment.UHFKillFragment;
import com.agp.uhf.fragment.UHFLightFragment;
import com.agp.uhf.fragment.UHFLocationFragment;
import com.agp.uhf.fragment.UHFLockFragment;
import com.agp.uhf.fragment.UHFRadarLocationFragment;
import com.agp.uhf.fragment.UHFReadTagFragment;
import com.agp.uhf.fragment.UHFReadWriteFragment;
import com.agp.uhf.fragment.UHFSetFragment;
import com.agp.uhf.fragment.UHFUpgradeFragment;
import com.agp.uhf.tools.ExportExcelAsyncTask;
import com.agp.uhf.tools.UIHelper;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.agp.uhf.RFIDWrapper;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * UHF使用demo
 * 1、在操作设备前需要调用 init()打开设备，使用完后调用 free() 关闭设备
 * 更多函数的使用方法请查看API说明文档
 *
 * @author zhopin
 * 更新于 2020年7月23日
 */
public class UHFMainActivity extends BaseTabFragmentActivity {

    private final static String TAG = "MainActivity";

    private FragmentTabHost mTabHost;
    private FragmentManager fm;
    public int selectIndex = -1;
    public ArrayList<UHFTAGInfo> tagList = new ArrayList<UHFTAGInfo>();
    ;
    public boolean loopFlag = false;
    private PlaySoundThread playSoundThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkReadWritePermission();
        setTitle(String.format(getString(R.string.app_name) + "(v%s)", getVerName()));
        initSound();
        initUHF();
        initViewPageData();
    }


    protected void initViewPageData() {

        fm = getSupportFragmentManager();
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, fm, R.id.realtabcontent);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_scan)).setIndicator(getString(R.string.uhf_msg_tab_scan)),
                UHFReadTagFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getResources().getString(R.string.uhf_radar_loaction)).setIndicator(getResources().getString(R.string.uhf_radar_loaction)),
                UHFRadarLocationFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.location)).setIndicator(getString(R.string.location)), UHFLocationFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_set)).setIndicator(getString(R.string.uhf_msg_tab_set)),
                UHFSetFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_read_write)).setIndicator(getString(R.string.uhf_msg_tab_read_write)),
                UHFReadWriteFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_light)).setIndicator(getString(R.string.uhf_msg_tab_light)),
                UHFLightFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_lock)).setIndicator(getString(R.string.uhf_msg_tab_lock)),
                UHFLockFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.uhf_msg_tab_kill)).setIndicator(getString(R.string.uhf_msg_tab_kill)),
                UHFKillFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec("BlockWrite").setIndicator("BlockWrite"),
                BlockWriteFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec("BlockPermalock").setIndicator("BlockPermalock"),
                BlockPermalockFragment.class, null);

        mTabHost.addTab(mTabHost.newTabSpec(getString(R.string.action_rfid_upgrader)).setIndicator(getString(R.string.action_rfid_upgrader)),
                UHFUpgradeFragment.class, null);
    }

    @Override
    protected void onDestroy() {
        Log.e("zz_pp", "onDestroy()");
        releaseSoundPool();
        if (mReader != null) {
            mReader.free();
        }
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    @Override
    public void exportData() {
        // super.exportData();
        checkReadWritePermission();
        if (loopFlag) {
            UIHelper.ToastMessage(this, R.string.uhf_msg_scaning);
            return;
        }
        if (tagList == null || tagList.isEmpty()) {
            UIHelper.ToastMessage(this, R.string.uhf_msg_export_data_empty);
            return;
        }
        new ExportExcelAsyncTask(this, tagList).execute();
    }

    HashMap<Integer, Integer> soundMap = new HashMap<Integer, Integer>();
    private SoundPool soundPool;
    private float volumnRatio;
    private AudioManager am;

    private void initSound() {
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 5);
        soundMap.put(1, soundPool.load(this, R.raw.barcodebeep, 1));
        soundMap.put(2, soundPool.load(this, R.raw.serror, 1));
        am = (AudioManager) this.getSystemService(AUDIO_SERVICE);// 实例化AudioManager对象

        playSoundThread = new PlaySoundThread();
        playSoundThread.start();
    }

    private void releaseSoundPool() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    /**
     * 播放提示音
     *
     * @param id 成功1，失败2
     */
    public void playSound(int id) {
        float audioMaxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); // 返回当前AudioManager对象的最大音量值
        float audioCurrentVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);// 返回当前AudioManager对象的音量值
        volumnRatio = audioCurrentVolume / audioMaxVolume;
        try {
            soundPool.play(soundMap.get(id), volumnRatio, // 左声道音量
                    volumnRatio, // 右声道音量
                    1, // 优先级，0为最低
                    0, // 循环次数，0不循环，-1永远循环
                    1 // 回放速度 ，该值在0.5-2.0之间，1为正常速度
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkReadWritePermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
                finish();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }
    }

    private Toast toast;

    public void showToast(String text) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void showToast(int resId) {
        showToast(getString(resId));
    }


    public void playSoundDelayed(int speed) {
        playSoundThread.play(speed);
    }


    private Object objectLock = new Object();

    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        int interval = 500;
        long lastPlayTime = SystemClock.elapsedRealtime();

        @Override
        public void run() {
            while (!isStop) {
                long start = 0;
                synchronized (objectLock) {
                    while (!isStop) {
                        if (start == 0) {
                            start = SystemClock.elapsedRealtime();
                        } else {
                            if (SystemClock.elapsedRealtime() - start >= interval) {
                                break;
                            } else {
                                SystemClock.sleep(1);
                            }
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastPlayTime < 500) {
                    playSound(1);
                }
            }
        }

        public void play(int speed) {
            //speed 1-100;
            //100-1
            //99-10
            //98-20
            //97-30

            int t = 3;
            if (speed > 85) {
                t = 3;
            } else if (speed > 66) {
                t = 100 - speed;
            } else if (speed > 33) {
                t = (100 - speed) * 2;
            } else {
                t = (100 - speed) * 3;
            }

            interval = t;
            lastPlayTime = SystemClock.elapsedRealtime();
            // Log.i("UHFRadarLocationFrag", " interval=" + interval );
        }

        public void stopPlay() {
            isStop = true;
            synchronized (objectLock) {
                objectLock.notifyAll();
            }
        }
    }
}
