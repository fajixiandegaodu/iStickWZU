package com.example.a86151.stickapplication.center;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.List;

/***************************************************************************************************
 *以服务的形式提供BLE的连接、服务发现、特性值读写等功能
 **************************************************************************************************/
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.yxu.administrator.centroid.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.yxu.administrator.centroid.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.yxu.administrator.centroid.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.yxu.administrator.centroid.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.yxu.administrator.centroid.EXTRA_DATA";


    /***************************************************************************************************
     * 处理BLE GATT事件的回调函数
     **************************************************************************************************/
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        /***************************************************************************************************
         * 处理GATT连接状态改变事件的回调函数
         **************************************************************************************************/
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {//连接成功
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);//发送广播通知“连接成功”
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//断开连接
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);//发送广播通知“断开连接”
            }
        }
        /***************************************************************************************************
         * 处理GATT服务发现完成事件的回调函数
         **************************************************************************************************/
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);//发送广播通知服务发现完成
            }
        }
        /***************************************************************************************************
         * 处理GATT特性读取完成事件的回调函数
         **************************************************************************************************/
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);//发送广播通知特性读取完成
            }
        }
        /***************************************************************************************************
         * 处理GATT特性值改变的回调函数，特性为notify且发生改变时才会调用
         **************************************************************************************************/
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);//发送广播通知特性读取完成
        }
    };
    /***************************************************************************************************
     * 发送广播通知BLE GATT状态改变事件
     **************************************************************************************************/
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);//以action为参数
        sendBroadcast(intent);//发送广播
    }
    /***************************************************************************************************
     * 发送广播通知特性读取完成事件，以UUID和特性值作为额外参数
     **************************************************************************************************/
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);//以action为参数

        // For all other profiles, writes the data formatted in HEX.
        final byte[] datas = characteristic.getValue();//获取特性值
        if (datas != null && datas.length > 0)
        {
            String s = characteristic.getUuid().toString();//获取UUID
            for(byte data:datas)
            {
                s += (char)data;//特性值以存储值的形式追加到UUID后面
            }
            intent.putExtra(EXTRA_DATA, s);
            sendBroadcast(intent);//发送广播
        }

    }
    /***************************************************************************************************
     * LocalBinder类，getService函数返回BluetoothLeService的实例以便可以使用服务中的公用方法
     **************************************************************************************************/
    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    /***************************************************************************************************
     * 绑定服务时才会调用，必须要实现的方法
     **************************************************************************************************/
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    /***************************************************************************************************
     * 解绑时调用的函数
     **************************************************************************************************/
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /***************************************************************************************************
     * 初始化BLE，成功返回true，失败返回false
     **************************************************************************************************/
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /***************************************************************************************************
     * 以mac地址为参数连接蓝牙设备的GATT服务器
     **************************************************************************************************/
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // 之前已经连接过，重新连接
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
        //第一次，通过mac地址连接
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);//连接GATT服务器
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /***************************************************************************************************
     * 断开GATT服务器连接
     **************************************************************************************************/
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /***************************************************************************************************
     * 关闭GATT服务器
     **************************************************************************************************/
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /***************************************************************************************************
     * 读取GATT特性，结果在onCharacteristicRead函数中处理
     **************************************************************************************************/
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    /***************************************************************************************************
     * 写GATT特性，结果暂时不用处理
     **************************************************************************************************/
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }
    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    /***************************************************************************************************
     * 打开或关闭特性的notify功能
     **************************************************************************************************/
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    /***************************************************************************************************
     * 获取蓝牙设备支持的服务及对应的特性，在发现服务完成后才能调用
     **************************************************************************************************/
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
