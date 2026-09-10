package com.agp.uhf.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.agp.uhf.R;
import com.agp.uhf.tools.CheckUtils;
import com.agp.uhf.tools.NumberTool;
import com.agp.uhf.tools.StringUtils;
import com.agp.uhf.tools.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.exception.ConfigurationException;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestActivity extends Activity {
    MyAdapter adapter;
    TextView tvTime;
    TextView tv_count;
    TextView tv_total;
    TextView tvSpeed;
    long lastTime=0;
    int lastTags=0;
    public static ListView LvTags;
    private int total;
    private long time;
    public ArrayList<UHFTAGInfo> tagList = new ArrayList<UHFTAGInfo>();;
    RFIDWithUHFUART rfidWithUHFUART=null;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what==1){
                UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                addDataToList(info);
                lastTags++;
            }else if (msg.what==2){
                if(rfidWithUHFUART.isInventorying()){
                    handler.sendEmptyMessageDelayed(2,10);
                    setTotalTime();

                    if(SystemClock.elapsedRealtime() - lastTime>=1000){
                        lastTime=SystemClock.elapsedRealtime();
                        tvSpeed.setText(lastTags+"");
                        lastTags=0;
                    }
                }else {
                    handler.removeMessages(2);
                }
            }

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        tvTime = (TextView) findViewById(R.id.tvTime);
        tvTime.setText("0s");
        tv_count = (TextView) findViewById(R.id.tv_count);
        tv_total = (TextView) findViewById(R.id.tv_total);
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        LvTags = (ListView) findViewById(R.id.LvTags);
        adapter=new MyAdapter(this);
        LvTags.setAdapter(adapter);
        tv_count.setText(tagList.size()+"");
        tv_total.setText(total+"");
        try {
            rfidWithUHFUART= RFIDWithUHFUART.getInstance();
        } catch (ConfigurationException e) {

        }
       new InitTask().execute();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(rfidWithUHFUART!=null) {
            rfidWithUHFUART.free();
        }
        if(playSoundThread!=null){
            playSoundThread.stopPlay();
        }
        releaseSoundPool();
    }



    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == 139 || keyCode == 280 || keyCode == 291 || keyCode == 293 || keyCode == 294
                || keyCode == 311 || keyCode == 312 || keyCode == 313 || keyCode == 315) {
            if (event.getRepeatCount() == 0) {
                readTag();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void readTag() {
        if (!rfidWithUHFUART.isInventorying()) {// 识别标签
            clearData();
            rfidWithUHFUART.setInventoryCallback(new IUHFInventoryCallback() {
                @Override
                public void callback(UHFTAGInfo uhftagInfo) {
                    Message msg = handler.obtainMessage();
                    msg.obj = uhftagInfo;
                    msg.what = 1;
                    handler.sendMessage(msg);
                    playSoundThread.play();
                }
            });
            playSoundThread.cleanData();
            if (rfidWithUHFUART.startInventoryTag()) {
                time = SystemClock.elapsedRealtime();
                handler.sendEmptyMessageDelayed(2, 10);
            } else {
                UIHelper.ToastMessage(this, R.string.uhf_msg_inventory_open_fail);
            }
        } else {// 停止识别
            rfidWithUHFUART.stopInventory();
            setTotalTime();
        }
    }


    private void setTotalTime() {
        float useTime = (SystemClock.elapsedRealtime() - time) / 1000.0F;
        tvTime.setText(NumberTool.getPointDouble(1, useTime) + "s");
    }
    private void addDataToList(UHFTAGInfo info) {
        String epc=info.getEPC();
        if (StringUtils.isNotEmpty(epc)) {
            boolean[] exists=new boolean[1];
            int insertIndex = CheckUtils.getInsertIndex(tagList,info,exists);
            if (exists[0]) {
                info.setCount(tagList.get(insertIndex).getCount() + 1);
                tagList.set(insertIndex,info);
            } else {
                tagList.add(insertIndex,info);
                tv_count.setText(String.valueOf(adapter.getCount()));
            }
            tv_total.setText(String.valueOf(++total));
            adapter.notifyDataSetChanged();
        }
    }
    private void clearData() {
        lastTime=0;
        lastTags=0;
        tv_count.setText("0");
        tv_total.setText("0");
        tvTime.setText("0s");
        tvSpeed.setText("0");
        total = 0;
        tagList.clear();
        adapter.notifyDataSetChanged();
    }
    public class MyAdapter extends BaseAdapter {
        public final class ViewHolder {
            public TextView tvEPCTID;
            public TextView tvTagCount;
            public TextView tvTagRssi;
        }

        private LayoutInflater mInflater;
        public MyAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }
        public int getCount() {
            // TODO Auto-generated method stub
            return  tagList.size();
        }
        public Object getItem(int arg0) {
            // TODO Auto-generated method stub
            return  tagList.get(arg0);
        }
        public long getItemId(int arg0) {
            // TODO Auto-generated method stub
            return arg0;
        }
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                holder = new  ViewHolder();
                convertView = mInflater.inflate(R.layout.listtag_items, null);
                holder.tvEPCTID = (TextView) convertView.findViewById(R.id.TvTagUii);
                holder.tvTagCount = (TextView) convertView.findViewById(R.id.TvTagCount);
                holder.tvTagRssi = (TextView) convertView.findViewById(R.id.TvTagRssi);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            UHFTAGInfo uhftagInfo= tagList.get(position);
            String epcAndTidUser=uhftagInfo.getEPC();
            holder.tvEPCTID.setText(epcAndTidUser);
            holder.tvTagCount.setText(uhftagInfo.getCount()+"" );
            holder.tvTagRssi.setText(uhftagInfo.getRssi());
            return convertView;
        }

    }
    //*********************************************************
    private Object objectLock = new Object();
   PlaySoundThread playSoundThread = null;
    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
        long count = 0;
        long consumption = 0;

        @Override
        public void run() {
            while (!isStop) {
                if (queue.isEmpty()) {
                    synchronized (objectLock) {
                        try {
                            objectLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (rfidWithUHFUART.isInventorying()) {
                    playSound(1);
                    queue.poll();
                    consumption++;
                }
                if (count - consumption > 50) {
                    for (int k = 0; k < 25; k++) {
                        queue.poll();
                    }
                    consumption += 25;
                }
            }
        }

        public void play() {
            queue.offer(1);
            synchronized (objectLock) {
                objectLock.notifyAll();
                count++;
            }
        }

        public void cleanData() {
            count = 0;
            consumption = 0;
            queue.clear();
        }

        public void stopPlay() {
            isStop = true;
            count = 0;
            consumption = 0;
            queue.clear();
            synchronized (objectLock) {
                objectLock.notifyAll();
            }
        }
    }

    public class InitTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO Auto-generated method stub
            return rfidWithUHFUART.init(TestActivity.this);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mypDialog.cancel();
            if (!result) {
                Toast.makeText(TestActivity.this, "init fail", Toast.LENGTH_SHORT).show();
            }else {
                initSound();
            }
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mypDialog = new ProgressDialog(TestActivity.this);
            mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mypDialog.setMessage("init...");
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.show();
        }
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

        playSoundThread=new PlaySoundThread();
        playSoundThread.start();
    }

    private void releaseSoundPool() {
        if(soundPool != null) {
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
}