package com.qingniu.qnble.demo.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.qingniu.qnble.demo.R;
import com.qingniu.qnble.demo.adapter.ListAdapter;
import com.qingniu.qnble.demo.util.UserConst;
import com.qingniu.qnble.utils.QNLogUtils;
import com.qingniu.scale.constant.DecoderConst;
import com.qn.device.constant.QNIndicator;
import com.qn.device.constant.QNScaleStatus;
import com.qn.device.listener.QNBleConnectionChangeListener;
import com.qn.device.listener.QNLogListener;
import com.qn.device.listener.QNResultCallback;
import com.qn.device.listener.QNWspScaleDataListener;
import com.qn.device.out.QNBleApi;
import com.qn.device.out.QNBleDevice;
import com.qn.device.out.QNScaleData;
import com.qn.device.out.QNScaleItemData;
import com.qn.device.out.QNScaleStoreData;
import com.qn.device.out.QNUser;
import com.qn.device.out.QNWspConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * wsp 双模秤连接界面
 */

public class WspScaleActivity extends AppCompatActivity implements View.OnClickListener {


    @BindView(R.id.snTextView)
    TextView snTextView;

    public static Intent getCallIntent(Context context, QNBleDevice device, QNWspConfig qnWspConfig) {
        return new Intent(context, WspScaleActivity.class)
                .putExtra(UserConst.DEVICE, device)
                .putExtra(UserConst.WSPCONFIG, qnWspConfig);
    }

    @BindView(R.id.connectBtn)
    Button mConnectBtn;
    @BindView(R.id.registerUserIndex)
    TextView registerUserIndex;
    @BindView(R.id.statusTv)
    TextView mStatusTv;
    @BindView(R.id.weightTv)
    TextView mWeightTv;
    @BindView(R.id.back_tv)
    TextView mBackTv;
    @BindView(R.id.listView)
    ListView mListView;
    @BindView(R.id.stroteDataTest)
    Button stroteDataTest;


    private QNBleDevice mBleDevice;
    private List<QNScaleItemData> mDatas = new ArrayList<>();
    private QNBleApi mQNBleApi;

    private QNWspConfig mQnWiFiConfig;

    private boolean mIsConnected;

    private ListAdapter listAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wsp_scale);
        mQNBleApi = QNBleApi.getInstance(this);
        //此API是用来监听日志的，如果需要上传日志到服务器则可以使用，否则不需要设置
        mQNBleApi.setLogListener(new QNLogListener() {
            @Override
            public void onLog(String log) {
                // Log.e("test", log);
            }
        });
        ButterKnife.bind(this);
        initIntent();
        initView();
        initData();
    }

    private void initData() {
        initBleConnectStatus();
        initUserData(); //设置数据监听器,返回数据,需在连接当前设备前设置
    }

    private void initBleConnectStatus() {
        mQNBleApi.setBleConnectionChangeListener(new QNBleConnectionChangeListener() {
            //正在连接
            @Override
            public void onConnecting(QNBleDevice device) {
                setBleStatus(QNScaleStatus.STATE_CONNECTING);
            }

            //已连接
            @Override
            public void onConnected(QNBleDevice device) {
                setBleStatus(QNScaleStatus.STATE_CONNECTED);
            }

            @Override
            public void onServiceSearchComplete(QNBleDevice device) {

            }

            //正在断开连接，调用断开连接时，会马上回调
            @Override
            public void onDisconnecting(QNBleDevice device) {
                setBleStatus(QNScaleStatus.STATE_DISCONNECTING);
            }

            // 断开连接，断开连接后回调
            @Override
            public void onDisconnected(QNBleDevice device) {
                setBleStatus(QNScaleStatus.STATE_DISCONNECTED);
            }

            //出现了连接错误，错误码参考附表
            @Override
            public void onConnectError(QNBleDevice device, int errorCode) {
                Log.d("WspScaleActivity", "onConnectError:" + errorCode);
                setBleStatus(QNScaleStatus.STATE_DISCONNECTED);
            }

        });
    }

    private void connectQnWspDevice(QNBleDevice device) {

        mQNBleApi.connectWspDevice(device, mQnWiFiConfig, new QNResultCallback() {
            @Override
            public void onResult(int code, String msg) {
                QNLogUtils.log("WspScaleActivity", "wifi 配置code:" + code + ",msg:" + msg);
            }
        });
    }


    private void initUserData() {
        mQNBleApi.setDataListener(new QNWspScaleDataListener() {
            @Override
            public void wspRegisterUserComplete(QNBleDevice device, QNUser user) {
                Log.d("WspScaleActivity", "注册返回的用户索引：" + user.getIndex());
                registerUserIndex.setText(getResources().getString(R.string.register_user_index) + user.getIndex());
            }

            @Override
            public void wspLocationSyncStatus(QNBleDevice device, boolean success) {
                //bow 秤返回经纬度是否写入成功
            }

            @Override
            public void wspReadSnComplete(QNBleDevice device, String sn) {
                Log.d("WspScaleActivity", "获取的SN：" + sn);
                snTextView.setText("SN:"+sn);
            }

            @Override
            public void onGetUnsteadyWeight(QNBleDevice device, double weight) {
                Log.d("WspScaleActivity", "体重是:" + weight);
                mWeightTv.setText(initWeight(weight));
            }

            @Override
            public void onGetScaleData(QNBleDevice device, QNScaleData data) {
                Log.d("WspScaleActivity", "收到测量数据");
                onReceiveScaleData(data);
                QNScaleItemData fatValue = data.getItem(QNIndicator.TYPE_SUBFAT);
                if (fatValue != null) {
                    String value = fatValue.getValue() + "";
                    Log.d("WspScaleActivity", "收到皮下脂肪数据:" + value);
                }
                Log.d("WspScaleActivity", "加密hmac为:" + data.getHmac());
//                Log.d("WspScaleActivity", "收到体脂肪:"+data.getItem(QNIndicator.TYPE_BODYFAT).getValue());
            }

            @Override
            public void onGetStoredScale(QNBleDevice device, List<QNScaleStoreData> storedDataList) {
                Log.d("WspScaleActivity", "收到存储数据");
                if (storedDataList != null && storedDataList.size() > 0) {
                    QNScaleStoreData data = storedDataList.get(0);
                    for (int i = 0; i < storedDataList.size(); i++) {
                        Log.d("WspScaleActivity", "收到存储数据:" + storedDataList.get(i).getWeight());
                    }
                    data.setUser(mQnWiFiConfig.getCurUser());
                    QNScaleData qnScaleData = data.generateScaleData();
                    onReceiveScaleData(qnScaleData);

                }
            }

            @Override
            public void onGetElectric(QNBleDevice device, int electric) {
                String text = "收到电池电量百分比:" + electric;
                Log.d("WspScaleActivity", text);
                if (electric == DecoderConst.NONE_BATTERY_VALUE) {//获取电池信息失败
                    return;
                }
                Toast.makeText(WspScaleActivity.this, text, Toast.LENGTH_SHORT).show();
            }

            //测量过程中的连接状态
            @Override
            public void onScaleStateChange(QNBleDevice device, int status) {
                Log.d("WspScaleActivity", "秤的连接状态是:" + status);
                setBleStatus(status);
            }

            @Override
            public void onScaleEventChange(QNBleDevice device, int scaleEvent) {
                Log.d("WspScaleActivity", "秤的事件是:" + scaleEvent);
            }
        });
    }

    private void initIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            mBleDevice = intent.getParcelableExtra(UserConst.DEVICE);
            mQnWiFiConfig = intent.getParcelableExtra(UserConst.WSPCONFIG);
        }
    }

    private String initWeight(double weight) {
        int unit = mQNBleApi.getConfig().getUnit();
        return mQNBleApi.convertWeightWithTargetUnit(weight, unit);
    }

    private void initView() {
        mConnectBtn.setOnClickListener(this);
        mBackTv.setOnClickListener(this);
        listAdapter = new ListAdapter(mDatas, mQNBleApi, mQnWiFiConfig.getCurUser());
        mListView.setAdapter(listAdapter);
        listAdapter.notifyDataSetChanged();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        doDisconnect();
        mQNBleApi.setBleConnectionChangeListener(null);
        mQNBleApi.setDataListener(null);
    }


    private void onReceiveScaleData(QNScaleData md) {
        mDatas.clear();
        mDatas.addAll(md.getAllItem());
        listAdapter.notifyDataSetChanged();
    }

    private void setBleStatus(int bleStatus) {
        String stateString;
        String btnString;
        switch (bleStatus) {
            case QNScaleStatus.STATE_CONNECTING: {
                stateString = getResources().getString(R.string.connecting);
                btnString = getResources().getString(R.string.disconnected);
                mIsConnected = true;
                break;
            }
            case QNScaleStatus.STATE_CONNECTED: {
                stateString = getResources().getString(R.string.connected);
                btnString = getResources().getString(R.string.disconnected);
                mIsConnected = true;
                break;
            }
            case QNScaleStatus.STATE_DISCONNECTING: {
                stateString = getResources().getString(R.string.disconnect_in_progress);
                btnString = getResources().getString(R.string.connect);
                mIsConnected = false;

                break;
            }
            case QNScaleStatus.STATE_LINK_LOSS: {
                stateString = getResources().getString(R.string.connection_disconnected);
                btnString = getResources().getString(R.string.connect);
                mIsConnected = false;
                break;
            }
            case QNScaleStatus.STATE_START_MEASURE: {
                stateString = getResources().getString(R.string.measuring);
                btnString = getResources().getString(R.string.disconnected);
                break;
            }
            case QNScaleStatus.STATE_REAL_TIME: {
                stateString = getResources().getString(R.string.real_time_weight_measurement);
                btnString = getResources().getString(R.string.disconnected);
                break;
            }
            case QNScaleStatus.STATE_BODYFAT: {
                stateString = getResources().getString(R.string.impedance_measured);
                btnString = getResources().getString(R.string.disconnected);
                break;
            }
            case QNScaleStatus.STATE_HEART_RATE: {
                stateString = getResources().getString(R.string.measuring_heart_rate);
                btnString = getResources().getString(R.string.disconnected);
                break;
            }
            case QNScaleStatus.STATE_MEASURE_COMPLETED: {
                stateString = getResources().getString(R.string.measure_complete);
                btnString = getResources().getString(R.string.disconnected);
                break;
            }
            case QNScaleStatus.STATE_WIFI_BLE_START_NETWORK:
                stateString = getResources().getString(R.string.start_set_wifi);
                btnString = getResources().getString(R.string.disconnected);
                Log.d("WspScaleActivity", "开始设置WiFi");
                break;
            case QNScaleStatus.STATE_WIFI_BLE_NETWORK_FAIL:
                stateString = getResources().getString(R.string.failed_to_set_wifi);
                btnString = getResources().getString(R.string.disconnected);
                Log.d("WspScaleActivity", "设置WiFi失败");
                break;
            case QNScaleStatus.STATE_WIFI_BLE_NETWORK_SUCCESS:
                stateString = getResources().getString(R.string.success_to_set_wifi);
                btnString = getResources().getString(R.string.disconnected);
                Log.d("WspScaleActivity", "设置WiFi成功");
                break;
            default: {
                stateString = getResources().getString(R.string.connection_disconnected);
                btnString = getResources().getString(R.string.connect);
                mIsConnected = false;
                break;
            }
        }
        mStatusTv.setText(stateString);
        mConnectBtn.setText(btnString);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.connectBtn:
                if (mIsConnected) {
                    //已经连接,断开连接
                    this.doDisconnect();
                } else {
                    //断开连接,就开始连接
                    mDatas.clear();
                    listAdapter.notifyDataSetChanged();
                    connectQnWspDevice(mBleDevice);
                }
                break;
            case R.id.back_tv:
                doDisconnect();
                finish();
                break;
        }
    }


    private void doDisconnect() {
        mQNBleApi.disconnectDevice(mBleDevice, new QNResultCallback() {
            @Override
            public void onResult(int code, String msg) {
                Log.d("WspScaleActivity", "断开连接设备返回:" + msg);
            }
        });
    }

    @OnClick({R.id.stroteDataTest})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.stroteDataTest:
                /**
                 *  用户获取到服务器的数据用此API进行测量数据的生成（The data acquired by the user to the server is generated using this API for measurement data）
                 */

               /* hmac="3F828A0207EB762F0D12E1ED5345AF7D6907304A74A45990B254256AC08DAA76EEA778E4B50ACE92D47DA72DD7257F82734C33A56721D797FD932B3741E5C730F2901F7EFAA1755DD0683BABD0959BB1E82201C3B50B3E8A5360A3D57550CF446DC834B8FA2F0D16DA4C0797CC1C308E4253413D4AB90DC4093F8065199ABE8AB0C9D06E3172E511C54C7E5095BB92C753070DC0CEB5D64785C4577952B50465"
        // 解密后{"weight":25.35,"measure_time":"2019-05-06 14:02:51","mac":"F0:FE:6B:CB:75:6A","heart_rate":93,"resistance_50":1601,"resistance_500":65474,"model_id":"0005"}*/
                String hmac = "3F828A0207EB762F0D12E1ED5345AF7D6907304A74A45990B254256AC08DAA76EEA778E4B50ACE92D47DA72DD7257F82734C33A56721D797FD932B3741E5C730F2901F7EFAA1755DD0683BABD0959BB1E82201C3B50B3E8A5360A3D57550CF446DC834B8FA2F0D16DA4C0797CC1C308E4253413D4AB90DC4093F8065199ABE8AB0C9D06E3172E511C54C7E5095BB92C753070DC0CEB5D64785C4577952B50465";
                QNScaleData scaleData = mQNBleApi.generateScaleData(mQnWiFiConfig.getCurUser(), "0005",
                        25.35, 1601, 65474, 93, hmac, new Date(1557122571000L));
                if (null != scaleData) {
                    onReceiveScaleData(scaleData);
                }
                break;
        }
    }


}
