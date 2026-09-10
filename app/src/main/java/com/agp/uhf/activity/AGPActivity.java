package com.agp.uhf.activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTabHost;

import com.agp.uhf.RFIDWrapper;
import com.agp.uhf.fragment.AsociarFragment;
import com.agp.uhf.fragment.ConsultarFragment;

import com.agp.uhf.R;
import com.agp.uhf.fragment.LocationFragmentAGP;
import com.agp.uhf.fragment.RadarFragmentAGP;
import com.agp.uhf.tools.BarcodeManager;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHF;

import java.util.ArrayList;
import java.util.HashMap;

public class AGPActivity extends BaseTabFragmentActivity {

      public Fragment currentFragment;

    public int selectIndex = -1;

    public ArrayList<UHFTAGInfo> tagList = new ArrayList<UHFTAGInfo>();
    private FragmentTabHost tabHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agp);
        initUHF();
        initSound();

        tabHost = findViewById(android.R.id.tabhost);

        tabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);


        String epcSeleccionado = ""; // Recuperamos el EPC seleccionado de algún lugar, por ejemplo, de ConsultarFragment.

        // Si el EPC es seleccionado en otro fragmento, asignamos el valor al Bundle.
        Bundle args = new Bundle();
        args.putString("selectedEPC", epcSeleccionado);
        RadarFragmentAGP radarFragment = new RadarFragmentAGP();
        radarFragment.setArguments(args); // Le pasamos el Bundle con el EPC
        tabHost.addTab(
                tabHost.newTabSpec("asociar").setIndicator("ASOCIAR"),
                AsociarFragment.class, null);

        tabHost.addTab(
                tabHost.newTabSpec("consultar").setIndicator("CONSULTA"),
                ConsultarFragment.class, null);

        tabHost.addTab(
                tabHost.newTabSpec("radar").setIndicator("RADAR"),
                RadarFragmentAGP.class, null);

        tabHost.addTab(
                tabHost.newTabSpec("location").setIndicator("LOCATION"),
                LocationFragmentAGP.class, null);

        // Inicializar escáner global
        BarcodeManager.getInstance(this);
        Intent intent = new Intent("com.rscja.scanner.action.CONTINUOUS_SCAN_BARCODE_RFID");
        intent.putExtra("enable", false);
        sendBroadcast(intent);


    }

    private PlaySoundThread playSoundThread = null;
    private AudioManager am;
    private SoundPool soundPool;
    HashMap<Integer, Integer> soundMap = new HashMap<Integer, Integer>();
    private void initSound() {
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 5);
        soundMap.put(1, soundPool.load(this, R.raw.barcodebeep, 1));
        soundMap.put(2, soundPool.load(this, R.raw.serror, 1));
        am = (AudioManager) this.getSystemService(AUDIO_SERVICE);// 实例化AudioManager对象

        playSoundThread = new PlaySoundThread();
        playSoundThread.start();
    }

    private float volumnRatio;

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
    private void releaseSoundPool() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
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

    public void playSoundDelayed(int speed) {
        playSoundThread.play(speed);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BarcodeManager.close(); // Cierra escáner
    }

}
