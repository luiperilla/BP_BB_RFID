package com.agp.uhf.fragment;

import com.agp.uhf.tools.BarcodeManager;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.deviceapi.entity.BarcodeEntity;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentTransaction;

import com.agp.uhf.R;
import com.agp.uhf.activity.AGPActivity;
import com.agp.uhf.tools.UIHelper;
import com.agp.uhf.view.CircleSeekBar;
import com.agp.uhf.view.RadarView;
import com.rscja.deviceapi.entity.RadarLocationEntity;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback;
import com.agp.uhf.tools.EpcHolder;

import java.util.List;

public class RadarFragmentAGP extends KeyDwonFragment {

    public final String TAG = "UHFRadarLocationFrag";
    private AGPActivity mContext;

    private RadarView radarView;
    private EditText etEPC;
    private Button btStart;
    private Button btStop;
    private CircleSeekBar seekBarPower;
    private boolean isSingleLabel = false;
    private boolean inventoryFlag = false;
    private String targetEpc; // 定位标签号
    //    private final RadarBackgroundView.StartAngle radarAngle = new RadarBackgroundView.StartAngle(0);
//    private final List<RadarLocationEntity> radarTagList = new LinkedList<>();
    int progress = 5;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhf_radar_location, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (AGPActivity) getActivity();
        mContext.currentFragment = this;

        radarView = getView().findViewById(R.id.radarView);
        etEPC = getView().findViewById(R.id.etRadarEPC);
        Log.d(TAG, "DEBUG_RADAR EpcHolder.epcSeleccionado=" + EpcHolder.epcSeleccionado);
        String epc = EpcHolder.epcSeleccionado;
        if (epc != null && !epc.isEmpty()) {
            etEPC.setText(epc);
            targetEpc = epc;
        }

        // Configurar escáner para este fragmento
        BarcodeDecoder decoder = BarcodeManager.getInstance(mContext);
        decoder.setDecodeCallback(new BarcodeDecoder.DecodeCallback() {
            @Override
            public void onDecodeComplete(BarcodeEntity barcodeEntity) {
                if (barcodeEntity.getResultCode() == BarcodeDecoder.DECODE_SUCCESS) {
                    requireActivity().runOnUiThread(() -> {
                        if (etEPC.isEnabled()) {
                            etEPC.setText(barcodeEntity.getBarcodeData());
                            etEPC.setSelection(etEPC.getText().length());  // Mueve el cursor al final
                            targetEpc = barcodeEntity.getBarcodeData();     // También actualiza targetEpc
                        }
                    });
                }
            }
        });


        btStart = getView().findViewById(R.id.btRadarStart);
        btStop = getView().findViewById(R.id.btRadarStop);
        seekBarPower = getView().findViewById(R.id.seekBarPower);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocated();
            }
        });
        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocated();
            }
        });

        getView().post(new Runnable() {
            @Override
            public void run() {
                if (targetEpc != null && !targetEpc.isEmpty()) {
                    return;
                }
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.equals("")) {
                    etEPC.setText(selectItem);
                    targetEpc = selectItem;
                } else {
                    etEPC.setText("");
                }
            }
        });

    }

    Toast toast = null;

    private void showMSG(int power) {
        if (toast == null) {
            toast = new Toast(mContext);
        }
        toast.cancel();
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setText("功率:" + power);
        toast.show();
    }

    private Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint("LongLogTag")
    private void startLocated() {
        if (inventoryFlag) return;
        // Verificar si mReader está inicializado

        if (mContext.mReader == null) {
            Log.e(TAG, "mReader is not initialized");
            return; // Salir de la función si mReader es null
        }
        radarView.clearPanel();
        targetEpc = etEPC.getText().toString();


        boolean result = mContext.mReader.startRadarLocation(mContext, targetEpc, IUHF.Bank_EPC, 32, new IUHFRadarLocationCallback() {
            @Override
            public void getLocationValue(final List<RadarLocationEntity> list) {
//                Log.i(TAG, " list.size=" + list.size());
                radarView.bindingData(list, targetEpc);

//                mContext.playSound(1);

                if (!TextUtils.isEmpty(targetEpc)) {
                    for (int k = 0; k < list.size(); k++) {
                        //Log.i(TAG, " k=" + k + "  value=" + list.get(k).getValue());
                        if (list.get(k).getTag().equals(targetEpc)) {
                            mContext.playSoundDelayed(list.get(k).getValue());
                        }
                    }
                } else {
                    mContext.playSound(1);
                }
            }

            @Override
            public void getAngleValue(int angle) {
                ///Log.i(TAG, "angle=" + angle);
                radarView.setRotation(-angle);
            }
        });
        if (!result) {
            UIHelper.ToastMessage(mContext, "启动失败");
            return;
        }

        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress2, boolean fromUser) {
                Log.d(TAG, "  progress =" + progress2);
                progress = progress2;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "  onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int p = 35 - progress;
                mContext.mReader.setDynamicDistance(p);
                Log.d(TAG, "  onStopTrackingTouch  p=" + p + "  progress=" + progress);
                //  Toast.makeText(getContext(),"功率："+progress,Toast.LENGTH_SHORT).show();
            }
        });
        seekBarPower.setEnabled(true);
        inventoryFlag = true;
        btStart.setEnabled(false);
        etEPC.setEnabled(false);

        radarView.startRadar(); // 启动雷达扫描动画
        Log.i(TAG, "startLocated success");
    }

    @SuppressLint("LongLogTag")
    private void stopLocated() {
        if (!inventoryFlag) return;
        radarView.stopRadar();  // 停止雷达扫描动画

        boolean result = mContext.mReader.stopRadarLocation();
        if (!result) {
            //停止失败
            Log.e(TAG, "stopLocated failure");
            mContext.playSound(2);
            Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "stopLocated success");
            inventoryFlag = false;
            btStart.setEnabled(true);
            etEPC.setEnabled(true);
        }
        seekBarPower.setOnSeekBarChangeListener(null);
        seekBarPower.setProgress(5);
        seekBarPower.setEnabled(false);
    }

    @Override
    public void myOnKeyDwon() {
        if (inventoryFlag) {
            stopLocated();
        } else {
            startLocated();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        String epc = EpcHolder.epcSeleccionado;
        if (epc != null && !epc.isEmpty() && etEPC != null) {
            etEPC.setText(epc);
            targetEpc = epc;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocated();
        radarView.stopRadar();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 重新加载Fragment本身的页面
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.detach(this).attach(this).commit();

        getView().post(new Runnable() {
            @Override
            public void run() {
                if (targetEpc != null && !targetEpc.isEmpty()) {
                    return;
                }
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }
                if (selectItem != null && !selectItem.equals("")) {
                    etEPC.setText(selectItem);
                    targetEpc = selectItem;
                } else {
                    etEPC.setText("");
                }
            }
        });

    }
}

