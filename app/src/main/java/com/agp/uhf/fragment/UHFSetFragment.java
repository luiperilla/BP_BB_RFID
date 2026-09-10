package com.agp.uhf.fragment;


import com.agp.uhf.R;
import com.agp.uhf.activity.UHFMainActivity;
import com.agp.uhf.tools.StringUtils;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;
import com.rscja.deviceapi.entity.Gen2Entity;
import com.rscja.deviceapi.entity.InventoryModeEntity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import com.rscja.deviceapi.entity.FastInventoryEntity;

public class UHFSetFragment extends KeyDwonFragment implements OnClickListener {
    private static final String TAG = "UHFSetFragment";
    private UHFMainActivity mContext;

    private Button btnSetFre;
    private Button btnGetFre;
    private Spinner spFrequency;
    @ViewInject(R.id.ll_freHop)
    private LinearLayout ll_freHop;

    @ViewInject(R.id.spPower)
    private Spinner spPower;

    @ViewInject(R.id.spFreHop)
    private Spinner spFreHop; //频点列表
    @ViewInject(R.id.btnSetFreHop)
    private Button btnSetFreHop; //设置频点设置

    @ViewInject(R.id.btnSetProtocol)
    private Button btnSetProtocol; //设置协议
    @ViewInject(R.id.SpinnerAgreement)
    private Spinner SpinnerAgreement; //协议列表


    @ViewInject(R.id.btnSetLinkParams)
    private Button btnSetLinkParams; //设置链路参数
    @ViewInject(R.id.btnGetLinkParams)
    private Button btnGetLinkParams; //获取链路参数
    @ViewInject(R.id.splinkParams)
    private Spinner splinkParams; //链路参数列表


    @ViewInject(R.id.spMemoryBank)
    private Spinner spMemoryBank;   // 盘点区域
    @ViewInject(R.id.llMemoryBankParams)
    private LinearLayout llMemoryBankParams;
    @ViewInject(R.id.etOffset)
    private EditText etOffset;
    @ViewInject(R.id.etLength)
    private EditText etLength;
    private int[] arrayMemoryBankValue;
    @ViewInject(R.id.btnSetMemoryBank)
    Button btnSetMemoryBank;
    @ViewInject(R.id.btnGetMemoryBank)
    Button btnGetMemoryBank;


    @ViewInject(R.id.cbTagFocus)
    private CheckBox cbTagFocus; //打开tagFocus
    @ViewInject(R.id.cbFastID)
    private CheckBox cbFastID; //打开FastID

    @ViewInject(R.id.rb_America)
    private RadioButton rb_America; //美国频点
    @ViewInject(R.id.rb_Others)
    private RadioButton rb_Others; //其他频点
    private ArrayAdapter adapter; //频点列表适配器

    @ViewInject(R.id.btnFactoryReset)
    private Button btnFactoryReset;


    private DisplayMetrics metrics;
    private AlertDialog dialog;

    private Handler mHandler = new Handler();
    private int arrPow; //输出功率

    private String[] arrayMode;
    private List<Integer> arrayLinkValue;

    Spinner spSessionID, spInventoried;
    Button btnSetSession, btnGetSession;
    Button btnGetPower, btnSetPower;
    private RadioButton rbFastInventoryOpen, rbFastInventoryClose;
    private Button btnSetFastInventory, btnGetFastInventory;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_uhf_set, container, false);
        ViewUtils.inject(this, root);

        spSessionID = root.findViewById(R.id.spSessionID);
        spInventoried = root.findViewById(R.id.spInventoried);
        btnGetSession = root.findViewById(R.id.btnGetSession);
        btnSetSession = root.findViewById(R.id.btnSetSession);

        btnGetPower = root.findViewById(R.id.btnGetPower);
        btnSetPower = root.findViewById(R.id.btnSetPower);
        rbFastInventoryOpen = root.findViewById(R.id.rbFastInventoryOpen);
        rbFastInventoryClose = root.findViewById(R.id.rbFastInventoryClose);
        btnSetFastInventory = root.findViewById(R.id.btnSetFastInventory);
        btnGetFastInventory = root.findViewById(R.id.btnGetFastInventory);

        llMemoryBankParams.setVisibility(View.GONE);
        spMemoryBank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                llMemoryBankParams.setVisibility(position == 2 || position == 3 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        arrayMode = getResources().getStringArray(R.array.arrayMode);
        int[] arrayLink = getResources().getIntArray(R.array.arrayLinkValue);
        arrayLinkValue = new ArrayList<>();
        for (int j : arrayLink) {
            arrayLinkValue.add(j);
        }
        arrayMemoryBankValue = getResources().getIntArray(R.array.arrayMemoryBankValue);

        btnSetFre = (Button) getView().findViewById(R.id.btnSetFrequency);
        btnGetFre = (Button) getView().findViewById(R.id.btnGetFrequency);

        spFrequency = (Spinner) getView().findViewById(R.id.spFrequency);
        spFrequency.setOnItemSelectedListener(new MyOnTouchListener());

        btnSetFre.setOnClickListener(new SetFreOnclickListener());
        btnGetFre.setOnClickListener(new GetFreOnclickListener());

        btnSetFreHop.setOnClickListener(this);
        btnSetProtocol.setOnClickListener(this);

        btnSetLinkParams.setOnClickListener(this);
        btnGetLinkParams.setOnClickListener(this);

        btnSetMemoryBank.setOnClickListener(this);
        btnGetMemoryBank.setOnClickListener(this);

        btnGetSession.setOnClickListener(this);
        btnSetSession.setOnClickListener(this);

        btnGetPower.setOnClickListener(v -> getPower(true));
        btnSetPower.setOnClickListener(v -> setPower());

        btnGetFastInventory.setOnClickListener(this);
        btnSetFastInventory.setOnClickListener(this);

        cbTagFocus.setOnCheckedChangeListener(new OnMyCheckedChangedListener());
        cbFastID.setOnCheckedChangeListener(new OnMyCheckedChangedListener());
        String ver = mContext.mReader.getVersion();
        arrPow = R.array.arrayPower;
        ArrayAdapter adapter = ArrayAdapter.createFromResource(mContext, arrPow, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPower.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        new Thread(() -> {
            getFre(false);
            getLinkParams(false);
            getPower(false);
            getMomoryBank(false);
            getSession();
            getFastInventory();
        }).start();
    }

    /**
     * 工作模式下拉列表点击选中item监听
     */
    public class MyOnTouchListener implements AdapterView.OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (spFrequency.getSelectedItem().toString().equals(getString(R.string.United_States_Standard))) {
                //TODO ll_freHop.setVisibility(View.VISIBLE);
                rb_America.setChecked(true); //默认美国频点
            } else if (position != 3) {
                ll_freHop.setVisibility(View.GONE);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }

    public class SetFreOnclickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            String strMode = spFrequency.getSelectedItem().toString();
            int mode = getMode(strMode);
            Log.d(TAG, "setFrequencyMode mode=" + mode);
            if (mContext.mReader.setFrequencyMode((byte) mode)) {
                mContext.showToast(R.string.uhf_msg_set_frequency_succ);
            } else {
                mContext.showToast(R.string.uhf_msg_set_frequency_fail);
            }
        }
    }

    public void getFre(boolean showToast) {
        int mode = mContext.mReader.getFrequencyMode();
        Log.e(TAG, "getFrequencyMode()=" + mode);
        mHandler.post(() -> {
            if (mode != -1) {
                int count = spFrequency.getCount();
                int idx = getModeIndex(mode);
                //Log.e("TAG", "spMode  " + getResources().getStringArray(R.array.arrayMode).length + "  " + (idx > count - 1 ? count - 1 : idx));
                spFrequency.setSelection(Math.min(idx, count - 1));
                if (showToast) mContext.showToast(R.string.uhf_msg_read_frequency_succ);
            } else {
                if (showToast) mContext.showToast(R.string.uhf_msg_read_frequency_fail);
            }
        });
    }


    /**
     * 获取链路参数
     */
    public void getLinkParams(boolean showToast) {
        int link = mContext.mReader.getRFLink();
        Log.e(TAG, "getLinkParams()=" + link);

        mHandler.post(() -> {
            if (link == -1) {
                mContext.showToast(R.string.uhf_msg_get_para_fail);
                return;
            }
            if (arrayLinkValue.contains(link)) {
                int index = arrayLinkValue.indexOf(link);
                if (index < getResources().getStringArray(R.array.arrayLink).length) {
                    splinkParams.setSelection(index);
                    if (showToast) mContext.showToast(R.string.uhf_msg_get_para_succ);
                    return;
                }
            }
            if (showToast) mContext.showToast("RFLink = " + link);
        });
    }

    private int getMode(String modeName) {
        if (modeName.equals(getString(R.string.China_Standard_840_845MHz))) {
            return 0x01;
        } else if (modeName.equals(getString(R.string.China_Standard_920_925MHz))) {
            return 0x02;
        } else if (modeName.equals(getString(R.string.ETSI_Standard))) {
            return 0x04;
        } else if (modeName.equals(getString(R.string.United_States_Standard))) {
            return 0x08;
        } else if (modeName.equals(getString(R.string.Korea))) {
            return 0x16;
        } else if (modeName.equals(getString(R.string.Japan))) {
            return 0x32;
        } else if (modeName.equals(getString(R.string.South_Africa_915_919MHz))) {
            return 0x33;
        } else if (modeName.equals(getString(R.string.New_Zealand))) {
            return 0x34;
        } else if (modeName.equals(getString(R.string.Morocco))) {
            return 0x80;
        }
        return 0x08;
    }

    private String getModeName(int mode) {
        switch (mode) {
            case 0x01:
                return getString(R.string.China_Standard_840_845MHz);
            case 0x02:
                return getString(R.string.China_Standard_920_925MHz);
            case 0x04:
                return getString(R.string.ETSI_Standard);
            case 0x08:
                return getString(R.string.United_States_Standard);
            case 0x16:
                return getString(R.string.Korea);
            case 0x32:
                return getString(R.string.Japan);
            case 0x33:
                return getString(R.string.South_Africa_915_919MHz);
            case 0x34:
                return getString(R.string.New_Zealand);
            case 0x80:
                return getString(R.string.Morocco);
            default:
                return getString(R.string.United_States_Standard);
        }
    }


    private int getModeIndex(String modeName) {
        for (int i = 0; i < arrayMode.length; i++) {
            if (arrayMode[i].equals(modeName)) {
                return i;
            }
        }
        return 0;
    }

    private int getModeIndex(int mode) {
        return getModeIndex(getModeName(mode));
    }


    public class GetFreOnclickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            getFre(true);
        }
    }

    public class OnMyCheckedChangedListener implements CompoundButton.OnCheckedChangeListener {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switch (buttonView.getId()) {
                case R.id.cbTagFocus:
                    if (mContext.mReader.setTagFocus(isChecked)) {
                        if (isChecked) {
                            cbTagFocus.setText(R.string.tagFocus_off);
                        } else {
                            cbTagFocus.setText(R.string.tagFocus);
                        }
                        mContext.showToast(
                                R.string.uhf_msg_set_succ);
                    } else {
                        mContext.showToast(
                                R.string.uhf_msg_set_fail);
//                        mContext.playSound(2);
                    }
                    break;
                case R.id.cbFastID:
                    if (mContext.mReader.setFastID(isChecked)) {
                        if (isChecked) {
                            cbFastID.setText(R.string.fastID_off);
                        } else {
                            cbFastID.setText(R.string.fastID);
                        }
                        mContext.showToast(
                                R.string.uhf_msg_set_succ);
                    } else {
                        mContext.showToast(
                                R.string.uhf_msg_set_fail);
//                        mContext.playSound(2);
                    }
                    break;
            }
        }
    }

    public void getPower(boolean showToast) {
        int iPower = mContext.mReader.getPower();
        Log.i("UHFSetFragment", "OnClick_GetPower() iPower=" + iPower);
        mHandler.post(() -> {
            if (iPower > -1) {
                int position = iPower - 1;
                int count = spPower.getCount();
                spPower.setSelection(Math.min(position, count - 1));
                if (showToast) mContext.showToast(R.string.uhf_msg_read_power_succ);
            } else {
                if (showToast) mContext.showToast(R.string.uhf_msg_read_power_fail);
            }
        });
    }

    public void setPower() {
        int iPower = spPower.getSelectedItemPosition() + 1;
        Log.i("UHFSetFragment", "OnClick_SetPower() iPower=" + iPower);
        if (mContext.mReader.setPower(iPower)) {
            mContext.showToast(R.string.uhf_msg_set_power_succ);
        } else {
            mContext.showToast(R.string.uhf_msg_set_power_fail);
//            mContext.playSound(2);
        }
    }

    /**
     * 设置频点
     *
     * @param value 频点数值
     * @return 是否设置成功
     */
    private boolean setFreHop(float value) {
        boolean result = mContext.mReader.setFreHop(value);
        if (result) {

            mContext.showToast(
                    R.string.uhf_msg_set_frehop_succ);
        } else {
            mContext.showToast(
                    R.string.uhf_msg_set_frehop_fail);
//            mContext.playSound(2);
        }
        return result;
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.btnSetFreHop: //设置频点
//			showFrequencyDialog();
                View view = spFreHop.getSelectedView();
                if (view instanceof TextView) {
                    String freHop = ((TextView) view).getText().toString().trim();
                    setFreHop(Float.valueOf(freHop)); //设置频点
                }
                break;
            case R.id.btnSetProtocol: //设置协议
                if (mContext.mReader.setProtocol(SpinnerAgreement.getSelectedItemPosition())) {
                    mContext.showToast(R.string.setAgreement_succ);
                } else {
                    mContext.showToast(R.string.setAgreement_fail);
//                    mContext.playSound(2);
                }
                break;
            case R.id.btnSetLinkParams: //设置链路参数
                int index = splinkParams.getSelectedItemPosition();
                int link = arrayLinkValue.get(index);
                if (mContext.mReader.setRFLink(link)) {
                    mContext.showToast(R.string.uhf_msg_set_succ);
                } else {
                    mContext.showToast(R.string.uhf_msg_set_fail);
//                    mContext.playSound(2);
                }
                break;
            case R.id.btnGetLinkParams: //获取链路参数
                getLinkParams(true);
                break;
            case R.id.rbEPC:
                llMemoryBankParams.setVisibility(View.GONE);
                break;
            case R.id.btnSetMemoryBank:
                setMemoryBank();
                break;
            case R.id.btnGetMemoryBank:
                getMomoryBank(true);
                break;
            case R.id.btnGetSession:
                Log.e("getSession", String.valueOf(getSession()));
                if (getSession()) {
                    mContext.showToast(R.string.uhf_msg_get_para_succ);
                } else {
                    mContext.showToast(R.string.uhf_msg_get_para_fail);
                }
                break;
            case R.id.btnSetSession:
                setSession();
                break;
            case R.id.btnGetFastInventory:
                if (getFastInventory()) {
                    mContext.showToast(R.string.uhf_msg_get_para_succ);
                } else {
                    mContext.showToast(R.string.uhf_msg_get_para_fail);
                }
                break;
            case R.id.btnSetFastInventory:
                setFastInventory();
                break;
            default:
                break;
        }
    }

    private boolean getSession() {
        Gen2Entity entity = mContext.mReader.getGen2();
        if (entity != null) {
            mHandler.post(() -> {
                spSessionID.setSelection(entity.getQuerySession());
                spInventoried.setSelection(entity.getQueryTarget());
            });
            return true;
        }
        return false;
    }

    private void setSession() {
        int seesionid = spSessionID.getSelectedItemPosition();
        int inventoried = spInventoried.getSelectedItemPosition();
        if (seesionid < 0 || inventoried < 0) {
            return;
        }
        Gen2Entity p = mContext.mReader.getGen2();
        if (p != null) {
            p.setQueryTarget(inventoried);
            p.setQuerySession(seesionid);
            if (mContext.mReader.setGen2(p)) {
                mContext.showToast(R.string.uhf_msg_set_succ);
            } else {
                mContext.showToast(R.string.uhf_msg_set_fail);
            }
        } else {
            mContext.showToast(R.string.uhf_msg_set_fail);
        }
    }

    /**
     * 显示频点设置
     */
    private void showFrequencyDialog() {
        if (dialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//	        builder.setTitle(R.string.btSetFrequency);
            View view = getActivity().getLayoutInflater().inflate(R.layout.uhf_dialog_frequency, null);
            ListView listView = (ListView) view.findViewById(R.id.listView_frequency);
            ImageView iv = (ImageView) view.findViewById(R.id.iv_dismissDialog);
            iv.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    dialog.dismiss();
                }
            });

            String[] strArr = getResources().getStringArray(R.array.arrayFreHop);
            listView.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.item_text1, strArr));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    // TODO Auto-generated method stub
                    if (view instanceof TextView) {
                        TextView tv = (TextView) view;
                        float value = Float.valueOf(tv.getText().toString().trim());
                        setFreHop(value); //设置频点
                        dialog.dismiss();
                    }
                }

            });

            builder.setView(view);
            dialog = builder.create();
            dialog.show();
            dialog.setCanceledOnTouchOutside(false);

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = getWindowWidth() - 100;
            params.height = getWindowHeight() - 200;
            dialog.getWindow().setAttributes(params);
        } else {
            dialog.show();
        }
    }


    /**
     * 获取屏幕宽度
     *
     * @return
     */
    public int getWindowWidth() {
        if (metrics == null) {
            metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.widthPixels;
    }

    /**
     * 获取屏幕高度
     *
     * @return
     */
    public int getWindowHeight() {
        if (metrics == null) {
            metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.heightPixels;
    }

    @OnClick(R.id.rb_America)
    public void onClick_rbAmerica(View view) {

        adapter = ArrayAdapter.createFromResource(mContext, R.array.arrayFreHop_us, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFreHop.setAdapter(adapter);
    }

    @OnClick(R.id.rb_Others)
    public void onClick_rbOthers(View view) {

        adapter = ArrayAdapter.createFromResource(mContext, R.array.arrayFreHop, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFreHop.setAdapter(adapter);

    }

    private void getMomoryBank(boolean isToast) {
        InventoryModeEntity mode = mContext.mReader.getEPCAndTIDUserMode();
        if (mode == null) {
            if (isToast) mContext.showToast(R.string.get_succ);
            return;
        }
        int bank = mode.getMode();
        boolean result = false;
        for (int i = 0; i < arrayMemoryBankValue.length; i++) {
            if (bank == arrayMemoryBankValue[i]) {
                spMemoryBank.setSelection(i);
                mHandler.postDelayed(() -> {
                    if (bank == InventoryModeEntity.MODE_EPC_TID_USER) {
                        etOffset.setText(String.valueOf(mode.getUserOffset()));
                        etLength.setText(String.valueOf(mode.getUserLength()));
                    } else if (bank == InventoryModeEntity.MODE_EPC_RESERVED) {
                        etOffset.setText(String.valueOf(mode.getReservedOffset()));
                        etLength.setText(String.valueOf(mode.getReservedLength()));
                    }
                }, 50);
                result = true;
                break;
            }
        }
        if (isToast) {
            mContext.showToast(result ? getString(R.string.get_succ) : getString(R.string.get_fail) + " mode=" + mode.getMode());
        }

    }

    private void setMemoryBank() {
        if((spMemoryBank.getSelectedItemPosition() == 2 || spMemoryBank.getSelectedItemPosition() == 3)) {
            if (StringUtils.toInt(etOffset.getText().toString().trim(), Integer.MIN_VALUE) == Integer.MIN_VALUE) {
                mContext.showToast(R.string.uhf_msg_offset_error);
                return;
            }
            if (StringUtils.toInt(etLength.getText().toString().trim(), Integer.MIN_VALUE) == Integer.MIN_VALUE) {
                mContext.showToast(R.string.uhf_msg_length_error);
                return;
            }
        }
        int position = spMemoryBank.getSelectedItemPosition();
        boolean result = false;
        int offset = 0, length = 6;
        if (position == 0) {
            result = mContext.mReader.setEPCMode();
        } else if (position == 1) {
            result = mContext.mReader.setEPCAndTIDMode();
        } else if (position == 2) {
            offset = StringUtils.toInt(etOffset.getText().toString().trim(), 0);
            length = StringUtils.toInt(etLength.getText().toString().trim(), 6);
            result = mContext.mReader.setEPCAndTIDUserMode(offset, length);
        } else if (position == 3) {
            offset = StringUtils.toInt(etOffset.getText().toString().trim(), 0);
            length = StringUtils.toInt(etLength.getText().toString().trim(), 4);
            InventoryModeEntity entity = new InventoryModeEntity
                    .Builder()
                    .setMode(InventoryModeEntity.MODE_EPC_RESERVED)
                    .setReservedOffset(offset)
                    .setReservedLength(length)
                    .build();
            result = mContext.mReader.setEPCAndTIDUserMode(entity);
        } else if (position == 4) {
            InventoryModeEntity entity = new InventoryModeEntity
                    .Builder()
                    .setMode(InventoryModeEntity.MODE_LED_TAG)
                    .build();
            result = mContext.mReader.setEPCAndTIDUserMode(entity);
        }

        mContext.showToast(result ? R.string.setting_succ : R.string.setting_fail);
    }


    private boolean getFastInventory() {
        final int result = mContext.mReader.getFastInventoryMode().getCr();
        Log.e("TAG", "getFastInventory: " + result);
        if (result >= 0) {
            mContext.runOnUiThread(() -> {
                if (result == 1) {
                    rbFastInventoryOpen.setChecked(true);
                    rbFastInventoryClose.setChecked(false);
                } else {
                    rbFastInventoryOpen.setChecked(false);
                    rbFastInventoryClose.setChecked(true);
                }
            });
            return true;
        }
        return false;
    }

    private void setFastInventory() {
        if (!rbFastInventoryOpen.isChecked() && !rbFastInventoryClose.isChecked()) {
            return;
        }
        boolean result = mContext.mReader.setFastInventoryMode(new FastInventoryEntity(rbFastInventoryOpen.isChecked() ? 1 : 0));
        mContext.showToast(result ? R.string.setting_succ : R.string.setting_fail);
    }


    @OnClick(R.id.btnFactoryReset)
    public void btnFactoryResetClick(View view) {
        if (mContext.mReader.factoryReset()) {
            mContext.showToast(R.string.reset_succ);
            new Thread(() -> {
                getFre(false);
                getLinkParams(false);
                getPower(false);
                getMomoryBank(false);
                getSession();
                getFastInventory();
            }).start();
        } else {
            mContext.showToast(R.string.reset_fail);
        }
    }


}
