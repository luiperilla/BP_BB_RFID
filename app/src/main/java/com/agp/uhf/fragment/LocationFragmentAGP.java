package com.agp.uhf.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

import com.agp.uhf.R;
import com.agp.uhf.activity.AGPActivity;
import com.agp.uhf.activity.UHFMainActivity;
import com.agp.uhf.tools.UIHelper;
import com.agp.uhf.view.CircleSeekBar;
import com.agp.uhf.view.UhfLocationCanvasView;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFLocationCallback;

import com.agp.uhf.tools.BarcodeManager;
import com.rscja.barcode.BarcodeDecoder;
import com.rscja.deviceapi.entity.BarcodeEntity;
import com.agp.uhf.tools.EpcHolder;



public class LocationFragmentAGP extends KeyDwonFragment {

    String TAG = "UHF_LocationFragment";
    private AGPActivity mContext;
    private UhfLocationCanvasView llChart;
    private EditText etEPC;
    private Button btStart, btStop;
    private CircleSeekBar seekBarPower;
    final int EPC = 2;
    int progress = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhflocation, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
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
                        }
                    });
                }
            }
        });

        super.onActivityCreated(savedInstanceState);
        mContext = (AGPActivity) getActivity();
        mContext.currentFragment = this;
        llChart = mContext.findViewById(R.id.llChart);
        etEPC = mContext.findViewById(R.id.etEPC);
        btStart = mContext.findViewById(R.id.btStart);
        btStop = mContext.findViewById(R.id.btStop);

        seekBarPower = getView().findViewById(R.id.seekBarPower);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
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

        btStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocation();
            }
        });
        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocation();
            }
        });

        getView().post(new Runnable() {
            @Override
            public void run() {
                llChart.clean();
                String epcSeleccionado = EpcHolder.epcSeleccionado;
                if (epcSeleccionado != null && !epcSeleccionado.isEmpty()) {
                    etEPC.setText(epcSeleccionado);
                    return;
                }
                String selectItem = null;
                if (mContext.tagList.size() > mContext.selectIndex && mContext.selectIndex >= 0) {
                    selectItem = mContext.tagList.get(mContext.selectIndex).getEPC();
                }

                if (selectItem != null) {
                    etEPC.setText(selectItem);
                } else {
                    etEPC.setText("");
                }
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        String epc = EpcHolder.epcSeleccionado;
        if (epc != null && !epc.isEmpty() && etEPC != null) {
            etEPC.setText(epc);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

    }

    @Override
    public void myOnKeyDwon() {
        if (btStart.isEnabled()) {
            startLocation();
        } else {
            stopLocation();
        }
    }

    private void startLocation() {
        String epc = etEPC.getText().toString();
        if (epc.equals("")) {
            UIHelper.ToastMessage(mContext, R.string.location_fail);
            return;
        }
        boolean result = mContext.mReader.startLocation(mContext, epc, IUHF.Bank_EPC, 32, new IUHFLocationCallback() {
            @Override
            public void getLocationValue(int value, boolean valid) {
                llChart.setData(value);
                Log.i(TAG, "value:" + value);
                if (valid) {
                    mContext.playSoundDelayed(value);
                }
            }

        });
        if (!result) {
            UIHelper.ToastMessage(mContext, R.string.psam_msg_fail);
            return;
        }
        seekBarPower.setEnabled(true);
        btStart.setEnabled(false);
        etEPC.setEnabled(false);
    }

    public void stopLocation() {
        mContext.mReader.stopLocation();
        btStart.setEnabled(true);
        etEPC.setEnabled(true);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
    }


}
