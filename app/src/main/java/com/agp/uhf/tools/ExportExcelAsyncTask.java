package com.agp.uhf.tools;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;

import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExportExcelAsyncTask extends AsyncTask<String, Integer, Boolean> {
     ArrayList<UHFTAGInfo> tagList;
      protected ProgressDialog mypDialog;
     protected Activity mContxt;
     boolean isSotp = false;
    String pathRoot = Environment.getExternalStorageDirectory() + File.separator + "UHF_exportData";
    String path = pathRoot + File.separator + GetTimesyyyymmddhhmmss() + ".xls";

    public ExportExcelAsyncTask(Activity act, ArrayList<UHFTAGInfo> tagList) {
        this.mContxt = act;
        this.tagList = tagList;
        File file = new File(pathRoot);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

        @Override
        protected Boolean doInBackground(String... params) {
            // TODO Auto-generated method stub
            boolean result = false;
            File f=new File("sdcard/uhf");
            if(!f.exists()){
                if(!f.mkdirs()){
                    return false;
                }
            }


            File file = new File(path);
            String[] h = new String[]{"EPC", "TID","USER","COUNT","RSSI",};//{"EPC", "TID", "COUNT", "RSSI"};
            ExcelUtils excelUtils = new ExcelUtils();
            excelUtils.createExcel(file, h);
            int size = tagList.size();
            List<String[]> list = new ArrayList<>();
            try {
                for (int k = 0; !isSotp && k < size; k++) {
                    String epc=  tagList.get(k).getEPC();
                    String tid=  tagList.get(k).getTid();
                    String user=  tagList.get(k).getUser();
                    String count=  tagList.get(k).getCount()+"";
                    String rssi=  tagList.get(k).getRssi();
                    int pro = (int) (div(k + 1, size, 2) * 100);
                    publishProgress(pro);

                    String[] data = new String[]{
                            epc,
                            tid,
                            user,
                            count,
                            rssi,
                    };
                    list.add(data);

                    StringBuilder stringBuilder=new StringBuilder();
                    stringBuilder.append(epc);
                    if(!TextUtils.isEmpty(tid)){
                        stringBuilder.append(",");
                        stringBuilder.append(tid);
                    }
                    if(!TextUtils.isEmpty(user)){
                        stringBuilder.append(",");
                        stringBuilder.append(user);
                    }

                }

            }catch (Exception ex){

            }

            publishProgress(101);
            excelUtils.writeToExcel(list);
            notifySystemToScan(file);
            sleepTime(3000);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mypDialog.cancel();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (values[0] == 101) {
                mypDialog.setMessage("path:" + path);
            } else {
                mypDialog.setProgress(values[0]);
            }
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            mypDialog = new ProgressDialog(mContxt);
            mypDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mypDialog.setMessage("...");
            mypDialog.setCanceledOnTouchOutside(false);
            mypDialog.setMax(100);
            mypDialog.setProgress(0);

            mypDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    isSotp = true;
                }
            });

            if (mContxt != null) {
                mypDialog.show();
            }
        }
        public String GetTimesyyyymmddhhmmss() {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
            String dt = formatter.format(curDate);
            return dt;
        }
        private float div(float v1, float v2, int scale) {
            BigDecimal b1 = new BigDecimal(Float.toString(v1));
            BigDecimal b2 = new BigDecimal(Float.toString(v2));
            return b1.divide(b2, scale, BigDecimal.ROUND_HALF_UP).floatValue();
        }
        public void notifySystemToScan(File file) {
            // mLogUtils.info
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            if (file.exists()) {
                Uri uri = Uri.fromFile(file);
                intent.setData(uri);
                mContxt.sendBroadcast(intent);
            }
        }
        private void sleepTime(long time) {
            try {
                Thread.sleep(time);
            } catch (Exception ex) {
            }
        }


}
