package com.agp.uhf.fragment;


import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

import com.agp.uhf.R;
import com.agp.uhf.activity.UHFMainActivity;
import com.agp.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.utility.StringUtility;


public class UHFReadWriteFragment extends KeyDwonFragment implements OnClickListener {
    private UHFMainActivity mContext;

    Spinner SpinnerBank;
    EditText EtPtr;
    EditText EtLen;
    EditText EtAccessPwd;
    EditText EtData;
    Button BtRead, BtWrite;

    CheckBox cb_filter;
    EditText etPtr_filter;
    EditText etData_filter;
    EditText etLen_filter;
    RadioButton rbEPC_filter;
    RadioButton rbTID_filter;
    RadioButton rbUser_filter;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.uhf_read_write_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        SpinnerBank = (Spinner) getView().findViewById(R.id.SpinnerBank);
        EtPtr = (EditText) getView().findViewById(R.id.EtPtr);
        EtLen = (EditText) getView().findViewById(R.id.EtLen);
        EtAccessPwd = (EditText) getView().findViewById(R.id.EtAccessPwd);
        EtData = (EditText) getView().findViewById(R.id.EtData);
        etLen_filter = (EditText) getView().findViewById(R.id.etLen_filter);

        cb_filter = (CheckBox) getView().findViewById(R.id.cb_filter);
        etPtr_filter = (EditText) getView().findViewById(R.id.etPtr_filter);
        etData_filter = (EditText) getView().findViewById(R.id.etData_filter);
        rbEPC_filter = (RadioButton) getView().findViewById(R.id.rbEPC_filter);
        rbEPC_filter.setOnClickListener(this);
        rbTID_filter = (RadioButton) getView().findViewById(R.id.rbTID_filter);
        rbTID_filter.setOnClickListener(this);
        rbUser_filter = (RadioButton) getView().findViewById(R.id.rbUser_filter);
        rbUser_filter.setOnClickListener(this);

        BtRead = (Button) getView().findViewById(R.id.BtRead);
        BtWrite = (Button) getView().findViewById(R.id.BtWrite);
        BtRead.setOnClickListener(v -> read());
        BtWrite.setOnClickListener(v -> write());

        EtData.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                EtLen.setText(String.valueOf(s.toString().trim().length() / 4));
            }
        });
        etData_filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                etLen_filter.setText(String.valueOf(s.toString().trim().length() * 4));
            }
        });


        cb_filter.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    String data = etData_filter.getText().toString().trim();
                    String rex = "[\\da-fA-F]*"; //匹配正则表达式，数据为十六进制格式
                    if (data.isEmpty() || !data.matches(rex)) {
                        mContext.showToast(getString(R.string.uhf_msg_filter_data_must_hex));
                        cb_filter.setChecked(false);
                        return;
                    }
                }
            }
        });
        SpinnerBank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String element = adapterView.getItemAtPosition(i).toString();// 得到spanner的值
                EtPtr.setText(element.equals("EPC") ? "2" : "0");
                EtLen.setText(element.equals("RESERVED") ? "4" : "6");
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.rbEPC_filter:
                if (rbEPC_filter.isChecked()) {
                    etPtr_filter.setText("32");
                }
                break;
            case R.id.rbTID_filter:
                if (rbTID_filter.isChecked()) {
                    etPtr_filter.setText("0");
                }
                break;
            case R.id.rbUser_filter:
                if (rbUser_filter.isChecked()) {
                    etPtr_filter.setText("0");
                }
                break;
        }
    }


    private void read() {
        String ptrStr = EtPtr.getText().toString().trim();
        if (ptrStr.equals("")) {
            mContext.showToast(R.string.uhf_msg_addr_not_null);
            return;
        } else if (!TextUtils.isDigitsOnly(ptrStr)) {
            mContext.showToast(R.string.uhf_msg_addr_must_decimal);
            return;
        }

        String cntStr = EtLen.getText().toString().trim();
        if (cntStr.equals("")) {
            mContext.showToast(R.string.uhf_msg_len_not_null);
            return;
        } else if (!TextUtils.isDigitsOnly(cntStr)) {
            mContext.showToast(R.string.uhf_msg_len_must_decimal);
            return;
        }

        String pwdStr = EtAccessPwd.getText().toString().trim();
        if (!TextUtils.isEmpty(pwdStr)) {
            if (pwdStr.length() != 8) {
                mContext.showToast(R.string.uhf_msg_addr_must_len8);
                return;
            } else if (!mContext.vailHexInput(pwdStr)) {
                mContext.showToast(R.string.rfid_mgs_error_nohex);
                return;
            }
        } else {
            pwdStr = "00000000";
        }

        boolean result = false;
        int Bank = SpinnerBank.getSelectedItemPosition();

        if (cb_filter.isChecked()) { //  过滤
            if (etPtr_filter.getText().toString().isEmpty()) {
                mContext.showToast(getString(R.string.uhf_msg_filter_addr_not_null));
                return;
            }
            if (etLen_filter.getText().toString().isEmpty()) {
                mContext.showToast(getString(R.string.uhf_msg_filter_len_not_null));
                return;
            }
            if (etData_filter.getText().toString().isEmpty()) {
                mContext.showToast(getString(R.string.uhf_msg_filter_data_not_null));
                return;
            }

            int filterPtr = Integer.parseInt(etPtr_filter.getText().toString());
            String filterData = etData_filter.getText().toString();
            int filterCnt = Integer.parseInt(etLen_filter.getText().toString());
            int filterBank = RFIDWithUHFUART.Bank_EPC;
            if (rbEPC_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_EPC;
            } else if (rbTID_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_TID;
            } else if (rbUser_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_USER;
            }
            String data = mContext.mReader.readData(pwdStr,
                    filterBank,
                    filterPtr,
                    filterCnt,
                    filterData,
                    Bank,
                    Integer.parseInt(ptrStr),
                    Integer.parseInt(cntStr)
            );
            if (data != null && data.length() > 0) {
                result = true;
                EtData.setText(data);
            } else {
                result = false;
                mContext.showToast(R.string.uhf_msg_read_data_fail);
            }
        } else {
            String data = mContext.mReader.readData(pwdStr,
                    Bank,
                    Integer.parseInt(ptrStr),
                    Integer.parseInt(cntStr)
            );
            if (!TextUtils.isEmpty(data)) {
                result = true;
                EtData.setText(data);
            } else {
                result = false;
                mContext.showToast(R.string.uhf_msg_read_data_fail);
            }
        }
        if (result) {
            mContext.playSound(1);
        } else {
            mContext.playSound(2);
        }
    }


    private void write() {
        String strPtr = EtPtr.getText().toString().trim();
        if (StringUtils.isEmpty(strPtr)) {
            mContext.showToast(R.string.uhf_msg_addr_not_null);
            return;
        } else if (!StringUtility.isDecimal(strPtr)) {
            mContext.showToast(R.string.uhf_msg_addr_must_decimal);
            return;
        }

        String strPWD = EtAccessPwd.getText().toString().trim();// 访问密码
        if (StringUtils.isNotEmpty(strPWD)) {
            if (strPWD.length() != 8) {
                mContext.showToast(R.string.uhf_msg_addr_must_len8);
                return;
            } else if (!mContext.vailHexInput(strPWD)) {
                mContext.showToast(R.string.rfid_mgs_error_nohex);
                return;
            }
        } else {
            strPWD = "00000000";
        }

        String strData = EtData.getText().toString().trim();// 要写入的内容
        if (StringUtils.isEmpty(strData)) {
            mContext.showToast(R.string.uhf_msg_write_must_not_null);
            return;
        } else if (!mContext.vailHexInput(strData)) {
            mContext.showToast(R.string.rfid_mgs_error_nohex);
            return;
        }

        // 多字单次
        String cntStr = EtLen.getText().toString().trim();
        if (StringUtils.isEmpty(cntStr)) {
            mContext.showToast(R.string.uhf_msg_len_not_null);
            return;
        } else if (!StringUtility.isDecimal(cntStr)) {
            mContext.showToast(R.string.uhf_msg_len_must_decimal);
            return;
        }

        if ((strData.length()) % 4 != 0) {
            mContext.showToast(R.string.uhf_msg_write_must_len4x);
            return;
        } else if (!mContext.vailHexInput(strData)) {
            mContext.showToast(R.string.rfid_mgs_error_nohex);
            return;
        }

        int writeLen = Integer.parseInt(cntStr);
        int writePtr = Integer.parseInt(strPtr);
        if (strData.length() / 4 < writeLen) {
            mContext.showToast("写入的内容和长度不匹配!");
            return;
        }

        boolean result = false;
        int Bank = SpinnerBank.getSelectedItemPosition();
        if (cb_filter.isChecked()) { // 指定标签
            if (etPtr_filter.getText().toString().isEmpty()) {
                etPtr_filter.setText("0");
            }
            if (etLen_filter.getText().toString().isEmpty()) {
                mContext.showToast(getString(R.string.uhf_msg_filter_len_not_null));
                return;
            }

            int filterPtr = Integer.parseInt(etPtr_filter.getText().toString());
            String filterData = etData_filter.getText().toString();
            int filterCnt = Integer.parseInt(etLen_filter.getText().toString());
            int filterBank = RFIDWithUHFUART.Bank_EPC;
            if (rbEPC_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_EPC;
            } else if (rbTID_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_TID;
            } else if (rbUser_filter.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_USER;
            }

            if (writeLen > 32) {
                int count = writeLen / 32 + writeLen % 32;
                int currTotal = writeLen;
                int cuurStart = writePtr;
                for (int k = 0; k < count; k++) {
                    if (mContext.mReader.writeData(strPWD,
                            filterBank,
                            filterPtr,
                            filterCnt,
                            filterData,
                            Bank,
                            cuurStart,
                            Math.min(currTotal, 32),
                            strData
                    )) {
                        cuurStart = cuurStart + 32;
                        currTotal = currTotal - 32;
                        result = true;
                    } else {
                        mContext.showToast("" + cuurStart + "-" + (writePtr + writeLen - 1) + "写入失败!");
                        result = false;
                        break;
                    }
                }
            } else {
                if (mContext.mReader.writeData(strPWD,
                        filterBank,
                        filterPtr,
                        filterCnt,
                        filterData,
                        Bank,
                        writePtr,
                        writeLen,
                        strData)
                ) {
                    result = true;
                } else {
                    mContext.showToast(R.string.uhf_msg_write_fail);
                    result = false;
                }
            }

        } else {

            if (writeLen > 32) {
                int count = writeLen / 32 + writeLen % 32;
                int currTotal = writeLen;
                int cuurStart = writePtr;
                for (int k = 0; k < count; k++) {
                    if (mContext.mReader.writeData(strPWD,
                            Bank,
                            cuurStart,
                            Math.min(currTotal, 32),
                            strData)
                    ) {
                        cuurStart = cuurStart + 32;
                        currTotal = currTotal - 32;
                        result = true;
                    } else {
                        mContext.showToast("" + cuurStart + "-" + (writePtr + writeLen - 1) + "写入失败!");
                        result = false;
                        break;
                    }
                }
            } else {
                if (mContext.mReader.writeData(strPWD,
                        Bank,
                        Integer.parseInt(strPtr),
                        Integer.valueOf(cntStr), strData)
                ) {
                    result = true;
                } else {
                    result = false;
                    mContext.showToast(R.string.uhf_msg_write_fail);
                }
            }
        }
        if (!result) {
            mContext.playSound(2);
            //mContext.showToast( "msg: "+ErrorCodeManage.getMessage(mContext.mReader.getErrCode()));
        } else {
            mContext.showToast(getString(R.string.uhf_msg_write_succ));
            mContext.playSound(1);
        }
    }


    public void myOnKeyDwon() {
        read();
    }
}
