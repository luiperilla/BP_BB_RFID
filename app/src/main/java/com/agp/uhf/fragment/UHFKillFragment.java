package com.agp.uhf.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import com.agp.uhf.R;
import com.agp.uhf.activity.UHFMainActivity;
import com.agp.uhf.tools.StringUtils;
import com.agp.uhf.tools.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;

public class UHFKillFragment extends KeyDwonFragment implements OnClickListener {

    private static final String TAG = "UHFKillFragment";

    private UHFMainActivity mContext;

    EditText EtAccessPwd_Write;
    Button btnKill;

    LinearLayout llFilter;
    CheckBox cb_filter;
    EditText etPtr_filter, etLen_filter, etData_filter;
    RadioButton rbEPC_filter, rbTID_filter, rbUser_filter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.uhf_kill_fragment, container, false);
        inits(view);
        return view;
    }

    private void inits(View parent) {
        EtAccessPwd_Write = (EditText) parent.findViewById(R.id.EtAccessPwd_Write);
        btnKill = (Button) parent.findViewById(R.id.btnKill);

        llFilter = (LinearLayout) parent.findViewById(R.id.llFilter);
        cb_filter = (CheckBox) parent.findViewById(R.id.cb_filter);
        etPtr_filter = (EditText) parent.findViewById(R.id.etPtr_filter);
        etLen_filter = (EditText) parent.findViewById(R.id.etLen_filter);
        etData_filter = (EditText) parent.findViewById(R.id.etData_filter);
        rbEPC_filter = (RadioButton) parent.findViewById(R.id.rbEPC_filter);
        rbTID_filter = (RadioButton) parent.findViewById(R.id.rbTID_filter);
        rbUser_filter = (RadioButton) parent.findViewById(R.id.rbUser_filter);

        cb_filter.setOnClickListener(this);
        rbEPC_filter.setOnClickListener(this);
        rbTID_filter.setOnClickListener(this);
        rbUser_filter.setOnClickListener(this);
        llFilter.setOnClickListener(this);
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

        cb_filter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onFilterCheck(buttonView);
            }
        });

        btnKill.setOnClickListener(new btnKillOnClickListener());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(null);

        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
    }

    private void onFilterCheck(CompoundButton buttonView) {
        if (buttonView.isChecked()) {
            llFilter.setVisibility(View.VISIBLE);
        } else {
            llFilter.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cb_filter:
                onFilterCheck(cb_filter);
                break;
            case R.id.rbEPC_filter:
                etPtr_filter.setText("32");
                break;
            case R.id.rbTID_filter:
                etPtr_filter.setText("0");
                break;
            case R.id.rbUser_filter:
                etPtr_filter.setText("0");
                break;
        }
    }

    public class btnKillOnClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {

            String strPWD = EtAccessPwd_Write.getText().toString().trim();// 访问密码

            if (StringUtils.isNotEmpty(strPWD)) {
                if (strPWD.length() != 8) {
                    UIHelper.ToastMessage(mContext, R.string.uhf_msg_addr_must_len8);
                    return;
                } else if (!mContext.vailHexInput(strPWD)) {
                    UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nohex);
                    return;
                }
            } else {
                UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nopwd);
                return;
            }

            if (cb_filter.isChecked()) { // 过滤
                //----------------过滤----------------------------
                int filterPtr = StringUtils.toInt(etPtr_filter.getText().toString(), -1);
                int filterCnt = StringUtils.toInt(etLen_filter.getText().toString(), -1);
                String filterData = etData_filter.getText().toString();
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (filterPtr < 0) {
                    UIHelper.ToastMessage(mContext, R.string.uhf_msg_filter_addr_must_decimal);
                    return;
                }
                if (filterCnt < 0) {
                    UIHelper.ToastMessage(mContext, R.string.uhf_msg_filter_len_must_decimal);
                    return;
                }
                if (filterCnt > 0) {
                    if (filterData == null) filterData = "";
                    int flag = filterCnt / 8 + (filterCnt % 8 == 0 ? 0 : 1);
                    int dataLen = filterData.replace(" ", "").length() / 2;
                    if (dataLen < flag) {
                        UIHelper.ToastMessage(mContext, R.string.uhf_msg_set_filter_fail2);
                        return;
                    }
                }
                if (rbEPC_filter.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_EPC;
                } else if (rbTID_filter.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_TID;
                } else if (rbUser_filter.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_USER;
                }

                //-----------------------------------------------------
                if (mContext.mReader.killTag(strPWD, filterBank, filterPtr, filterCnt, filterData)) {
                    UIHelper.ToastMessage(mContext, R.string.rfid_mgs_kill_succ);
                    mContext.playSound(1);
                } else {
                    UIHelper.ToastMessage(mContext, R.string.rfid_mgs_kill_fail);
                    mContext.playSound(2);
                }
            } else {
                if (mContext.mReader.killTag(strPWD)) {
                    UIHelper.ToastMessage(mContext, getString(R.string.rfid_mgs_kill_succ));
                    mContext.playSound(1);
                } else {
                    UIHelper.ToastMessage(mContext, R.string.rfid_mgs_kill_fail);
                    mContext.playSound(2);
                }
            }
        }
    }

}
