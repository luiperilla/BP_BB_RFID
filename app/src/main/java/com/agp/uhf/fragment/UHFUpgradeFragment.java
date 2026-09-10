package com.agp.uhf.fragment;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.agp.uhf.R;
import com.agp.uhf.activity.UHFMainActivity;
import com.agp.uhf.filebrowser.FileManagerActivity;
import com.agp.uhf.tools.StringUtils;
import com.agp.uhf.tools.UIHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class UHFUpgradeFragment extends KeyDwonFragment {
    String TAG = "UHFUpgradeActivity_zp";
    private Button btnBrowser;
    private Button btnUpgrade;
    private EditText etPath;
    private String version = "";
    private TextView tvMsg;
    private RadioButton rb_uhf_module,rb_ex10;
    private GetPathBroadcastReceiver mGetpathReceiver = null;
    private UHFMainActivity mContext;
    private int upgradeType = 1;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_uhfupgrade, container, false);
        init(view);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mGetpathReceiver != null) {
            getActivity().unregisterReceiver(mGetpathReceiver);
        }
    }
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
    }
    private void init(View view) {
        btnBrowser = (Button) view.findViewById(R.id.btnBrowser);
        btnUpgrade = (Button) view.findViewById(R.id.btnUpgrade);
        etPath = (EditText) view.findViewById(R.id.et_file);

        tvMsg = (TextView) view.findViewById(R.id.tvMsg);
        rb_uhf_module = (RadioButton) view.findViewById(R.id.rb_uhf_module);
        rb_ex10 = (RadioButton) view.findViewById(R.id.rb_ex10);
        rb_uhf_module.setChecked(true);
        rb_uhf_module.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                upgradeType = 1;
            }
        });
        rb_ex10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                upgradeType = 3;
            }
        });
        btnBrowser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), FileManagerActivity.class);
                getActivity().startActivity(intent);
            }
        });
        btnUpgrade.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String filePath = etPath.getText().toString();
                if (StringUtils.isEmpty(filePath)) {
                    UIHelper.ToastMessage(getActivity(), R.string.up_msg_sel_file);
                    return;
                }
                if (filePath.toLowerCase().lastIndexOf(".bin") < 0) {
                    UIHelper.ToastMessage(getActivity(), R.string.msg_file_format_err);
                    return;
                }
                tvMsg.setText("");
                new UpgradeTask(filePath,upgradeType).execute();
            }
        });
        mGetpathReceiver = new GetPathBroadcastReceiver();
        IntentFilter filterPosition = new IntentFilter();
        filterPosition.addAction(FileManagerActivity.Path_ACTION);
        getActivity().registerReceiver(mGetpathReceiver, filterPosition);
    }
    public class UpgradeTask extends AsyncTask<String, Integer, Boolean> {
        ProgressDialog mypDialog;
        String mFileName;
        int type;
        public UpgradeTask(String filename, int type) {
            mFileName = filename;
            this.type=type;
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if(values[0] >0) {
                mypDialog.setMessage((values[0] * 100 / values[1]) + "% " + getActivity().getString(R.string.app_msg_Upgrade));
            }
            tvMsg.setText("version:" + version);
        }
        @Override
        protected Boolean doInBackground(String... params) {
            // TODO Auto-generated method stub
            boolean result = false;
            File uFile = new File(mFileName);
            if (!uFile.exists()) {
                return false;
            }
            long uFileSize = uFile.length();
            Log.d(TAG, "uFileSize=" + uFileSize);
            int packageCount = (int) (uFileSize / 64);
            Log.d(TAG, "packageCount=" + packageCount);
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(mFileName, "r");
            } catch (FileNotFoundException e) {
            }
            if (raf == null) {
                return false;
            }
            /*
            Log.d(TAG, "UHF上电");
            if (uhf != null && !uhf.isPowerOn()) {
                if (!uhf.init()) {
                    Log.d(TAG, "UHF上电失败");
                    return false;
                }
            }
            */
            if(type ==1){
                version = mContext.mReader.getVersion();//获取版本号
            }else {
                version = mContext.mReader.getHardwareVersion();//获取硬件版本号
            }
            publishProgress(  0, 0);

            Log.d(TAG, "UHF uhfJump2Boot");
            if (!mContext.mReader.uhfJump2Boot(type)) {
                Log.d(TAG, "uhfJump2Boot 失败");
                return false;
            }
            sleep(2000);
            Log.d(TAG, "UHF uhfStartUpdate");
            if (!mContext.mReader.uhfStartUpdate()) {
                Log.d(TAG, "uhfStartUpdate 失败");
                return false;
            }
            sleep(2000);
            int pakeSize = 64;
            byte[] currData = new byte[(int) uFileSize];
            for (int k = 0; k < packageCount; k++) {
                int index = k * pakeSize;
                try {
                    int rsize = raf.read(currData, index, pakeSize);
                    Log.d(TAG, "beginPack=" + index + " endPack=" + (index + pakeSize - 1) + " rsize=" + rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }

                if (mContext.mReader.uhfUpdating(Arrays.copyOfRange(currData, index, index + pakeSize))) {
                    result = true;
                    publishProgress(index + pakeSize, (int) uFileSize);
                    //sleep(10);
                } else {
                    Log.d(TAG, "uhfUpdating 失败");
                    stop();
                    return false;
                }

            }
            if (uFileSize % pakeSize != 0) {
                int index = packageCount * pakeSize;
                int len = (int) (uFileSize % pakeSize);
                try {
                    int rsize = raf.read(currData, index, len);
                    Log.d(TAG, "beginPack=" + index + " countPack=" + len + " rsize=" + rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }
                if (mContext.mReader.uhfUpdating(Arrays.copyOfRange(currData, index, index + len))) {
                    result = true;
                    publishProgress((int) uFileSize, (int) uFileSize);
                } else {
                    Log.d(TAG, "uhfUpdating 失败");
                    stop();
                    return false;
                }
            }
            stop();
            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mypDialog.cancel();
            etPath.setText("");
            if (!result) {
                UIHelper.ToastMessage(getActivity(), R.string.uhf_msg_upgrade_fail);
                tvMsg.setText(R.string.uhf_msg_upgrade_fail);
                tvMsg.setTextColor(Color.RED);
            } else {
                UIHelper.ToastMessage(getActivity(), R.string.uhf_msg_upgrade_succ);
                tvMsg.setText(R.string.uhf_msg_upgrade_succ);
                tvMsg.setTextColor(Color.GREEN);
            }
            sleep(2000);
            if(type == 1){
                tvMsg.setText(tvMsg.getText() + " version=" + mContext.mReader.getVersion());
            }else{
                tvMsg.setText(tvMsg.getText() + " version=" + mContext.mReader.getHardwareVersion());
            }


        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mypDialog = new ProgressDialog(getActivity());
            mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mypDialog.setMessage(getActivity().getString(R.string.app_msg_Upgrade));
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.setCancelable(false);
            mypDialog.show();
        }


        private void sleep(int time) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void stop() {
            Log.d(TAG, "UHF uhfStopUpdate");
            if (!mContext.mReader.uhfStopUpdate())
                Log.d(TAG, "uhfStopUpdate 失败");
            sleep(2000);
        }
    }

    private class GetPathBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FileManagerActivity.Path_ACTION)) {
                String strFilePath = intent.getStringExtra(FileManagerActivity.Path_Key);
                etPath.setText(strFilePath);
            }
        }
    }
    private void ss(){

    }
}
