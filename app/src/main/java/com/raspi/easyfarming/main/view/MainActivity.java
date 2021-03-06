package com.raspi.easyfarming.main.view;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.blankj.utilcode.util.ToastUtils;
import com.raspi.easyfarming.R;
import com.raspi.easyfarming.device.view.DeviceCenterFrag;
import com.raspi.easyfarming.spot.view.SpotCenterFrag;
import com.raspi.easyfarming.user.view.UserFrag;
import com.raspi.easyfarming.utils.gps.Location;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements AMapLocationListener {

    //常量
    private MainActivity self = MainActivity.this;
    private final String TAG ="MainAcitivity";

    //控件
    @BindView(R.id.main_bnv)
    BottomNavigationView bottomNavigationView;
    @BindView(R.id.main_vp)
    ViewPager viewPager;

    //数据
    private List<Fragment> fragments;
    private String username;
    private String phone;
    private String email;

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    //mqtt
    private String[] RECENVETOPICFORMAT = {"WARNING/DEVICE/1/#"};
    private MqttAndroidClient mqttAndroidClient;
    private String serverUri = "tcp://39.108.153.134:1883";
    private String deviceId = "websocket_client";
    private boolean connectSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(self);
        initView();//初始化控件
        initPager();//初始化ViewPager
        initBottomNav();//初始化bottomNavigationView
        initIntentObject();//初始化数据
        initGPS();//初始化定位
        //MQTT初始化
        initSDK(this, RECENVETOPICFORMAT);
        connectServer();
    }

    /**
     * 初始化Intent数据
     */
    private void initIntentObject() {

        Intent intent = getIntent();
        if(intent!=null&&intent.hasExtra("username")){
            username  = intent.getStringExtra("username");
        }
        if(intent!=null&&intent.hasExtra("email")){
            email  = intent.getStringExtra("email");
        }
        if(intent!=null&&intent.hasExtra("phone")){
            phone  = intent.getStringExtra("phone");
        }
    }


    /***********************    初始化    ****************************/


    /**
     * 初始化bottomNavigationView
     */
    private void initBottomNav() {
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int position = 0;
                switch (menuItem.getItemId()){
                    case R.id.main_bnv_device:
                        position = 0;
                        break;
                    case R.id.main_bnv_spot:
                        position  = 1;
                        break;
                    case R.id.main_bnv_user:
                        position = 2;
                        break;
                }
                viewPager.setCurrentItem(position);
                menuItem.setChecked(true);
                return false;
            }
        });
    }

    /**
     * 初始化分页
     */
    private void initPager() {
        viewPager.setOffscreenPageLimit(3);

        DeviceCenterFrag deviceCenterFrag = new DeviceCenterFrag();
        UserFrag userFrag = new UserFrag();
        SpotCenterFrag spotCenterFrag = new SpotCenterFrag();
        //添加fragment
        fragments = new ArrayList<Fragment>();
        fragments.add(deviceCenterFrag);
        fragments.add(spotCenterFrag);
        fragments.add(userFrag);

        //适配器
        viewPager.setAdapter(new FragmentStatePagerAdapter(getSupportFragmentManager()) {

            @Override
            public Fragment getItem(int position) {
                return fragments.get(position);
            }

            @Override
            public int getCount() {
                return fragments.size();
            }
        });

        //页面切换事件
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            //图标Id
            int[] itemIds = {
                    R.id.main_bnv_device,
                    R.id.main_bnv_spot,
                    R.id.main_bnv_user
            };

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                bottomNavigationView.setSelectedItemId(itemIds[position]);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    /**
     * 初始化控件
     */
    private void initView() {
        getSupportActionBar().hide();
        getWindow().setStatusBarColor(getResources().getColor(R.color.bnv_color));
    }

    /**
     * 初始化网络广播
     */
    private void initNetBoardcastReceiver() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // 请注意这里会有一个版本适配bug，所以请在这里添加非空判断
        if (connectivityManager != null) {
            connectivityManager.requestNetwork(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
                /**
                 * 网络可用的回调
                 */
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    Log.e(TAG, "onAvailable");
                }
                /**
                 * 网络丢失的回调
                 */
                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    ToastUtils.showShort("无可用的网络，请连接网络");
                }
            });
        }
    }

    /***********************    生命周期中的操作    ****************************/


    @Override
    protected void onResume() {
        super.onResume();
        initNetBoardcastReceiver();
    }

    @Override
    protected void onDestroy() {
        close();
        super.onDestroy();
    }

    /**************************** mqtt  ****************************************/
    /**
     * 初始化SDK
     *
     * @param context context
     */
    public void initSDK(Context context, final String[] topics) {
        mqttAndroidClient = new MqttAndroidClient(context.getApplicationContext(), serverUri, deviceId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    Log.d(TAG, "Reconnected to : " + serverURI);
                } else {
                    Log.d(TAG, "Connected to: " + serverURI);

                }
                connectSuccess = true;
                // 订阅
                subscribeToTopic(topics);
            }

            @Override
            public void connectionLost(Throwable cause) {
                connectSuccess = false;
                Log.e(TAG, "The Connection was lost.");
            }

            // THIS DOES NOT WORK!
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.d(TAG, "Incoming message: " + topic + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }


    /**
     * 连接远程服务
     */
    public void connectServer() {
        MqttConnectOptions mqttConnectOptions = initMqttConnectionOptions(deviceId, deviceId);
        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connectSuccess = true;
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Failed to connect to: " + serverUri);
                    exception.printStackTrace();
                    Log.d(TAG, "onFailure: " + exception.getCause());
                    connectSuccess = false;
                }
            });

        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    // 初始化Mqtt连接选项
    private MqttConnectOptions initMqttConnectionOptions(String userName, String password) {
        MqttConnectOptions mOptions = new MqttConnectOptions();
        mOptions.setAutomaticReconnect(true);//断开后，是否自动连接
        mOptions.setCleanSession(true);//是否清空客户端的连接记录。若为true，则断开后，broker将自动清除该客户端连接信息
        mOptions.setConnectionTimeout(100);//设置超时时间，单位为秒
        mOptions.setUserName(userName);//设置用户名。跟Client ID不同。用户名可以看做权限等级
        mOptions.setPassword(password.toCharArray());//设置登录密码
        mOptions.setKeepAliveInterval(200);//心跳时间，单位为秒。即多长时间确认一次Client端是否在线
        mOptions.setMaxInflight(10);//允许同时发送几条消息（未收到broker确认信息）
        mOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);//选择MQTT版本
        return mOptions;
    }

    /**
     * 订阅mqtt消息
     */
    private void subscribeToTopic(String[] topics) {
        try {
            if (topics == null || topics.length == 0) {
                return;
            }
            int[] qoc = new int[topics.length];
            IMqttMessageListener[] mqttMessageListeners = new IMqttMessageListener[topics.length];
            for (int i = 0; i < topics.length; i++) {
                IMqttMessageListener mqttMessageListener = new IMqttMessageListener() {
                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        // message Arrived!消息送达后做出的处理
                        Log.d(TAG, topic + " : " + new String(message.getPayload()));
                        handleReceivedMessage(new String(message.getPayload()), topic);
                    }
                };
                mqttMessageListeners[i] = mqttMessageListener;
                Log.d(TAG, "subscribeToTopic: qoc= " + qoc[i]);
            }
            mqttAndroidClient.subscribe(topics, qoc, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    Log.d(TAG, "Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    Log.d(TAG, "Failed to subscribe");
                }
            }, mqttMessageListeners);

        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }

    }

    // 处理消息
    private void handleReceivedMessage(final String message, final String gatewayId) {
        //可以发送一条广播通知程序
        Log.i(TAG, "topic"+gatewayId);
        Log.i(TAG, " receive : " + message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendSimplestNotificationWithAction();
            }
        });
    }


    /**************************************** 消息通知 ******************************************/
    /**
     * 通知实现
     */
    private void sendSimplestNotificationWithAction() {
        if(Build.VERSION.SDK_INT>=26){
            createChannel();
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = new NotificationCompat.Builder(this, "channel_01")
                .setContentTitle("警报")
                .setContentText("温度已经超出警告值")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_logo)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo))
                .setContentIntent(pi)
                .build();
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, notification);

    }

    /**
     * 用于兼容不同版本
     */
    @RequiresApi(api=26)
    public void createChannel(){
        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        //通知渠道组的id
        String group = "group_id";
        // 用户可见的通知渠道组名称.
        CharSequence group_name = getString(R.string.channel_group_name);
        //创建通知渠道组
        manager.createNotificationChannelGroup(new NotificationChannelGroup(group, group_name));
        /**
         * 创建通知渠道1
         */
        //渠道id
        String id = "channel_01";
        //用户可以看到的通知渠道的名字
        CharSequence name = getString(R.string.channel_name);
        //用户可以看到的通知渠道的描述
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        //配置通知渠道的属性
        channel.setDescription(description);
        //设置通知出现时的闪灯（如果android设备支持的话）
        channel.enableLights(true);
        channel.setLightColor(Color.GREEN);
        //设置通知出现时的震动（如果androd设备支持的话）
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
        //绑定通知渠道组
        channel.setGroup(group);
        //在notificationmanager中创建该通知渠道
        manager.createNotificationChannel(channel);
    }

    private void close() {
        if (mqttAndroidClient != null) {
            try {
                mqttAndroidClient.disconnect();
                mqttAndroidClient.unregisterResources();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    /*********************** 物理键重写 ******************************/
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK){
            moveTaskToBack(true);
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }
    /************************************ 定位******************************************/
    /**
     * 实现GPS的方法
     */
    public void initGPS() {
        //AmapGPS
        AMapLocationClient mlocationClient;
        AMapLocationClientOption mLocationOption = null;
        Log.e(TAG, "GPS", null);
        mlocationClient = new AMapLocationClient(getBaseContext());
        //初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        //设置定位监听
        mlocationClient.setLocationListener(this);
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption.setInterval(20000);
        //设置定位参数
        mlocationClient.setLocationOption(mLocationOption);
        // 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
        // 注意设置合适的定位时间的间隔（最小间隔支持为1000ms），并且在合适时间调用stopLocation()方法来取消定位请求
        // 在定位结束后，在合适的生命周期调用onDestroy()方法
        // 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
        //启动定位
        mlocationClient.startLocation();
    }

    /**
     * 定位监听事件
     *
     * @param aMapLocation
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                //定位成功回调信息，设置相关消息
                aMapLocation.getLocationType();//获取当前定位结果来源，如网络定位结果，详见定位类型表
                Location.INSTANCE.setLatitude(String.format("%.2f", aMapLocation.getLatitude()));//获取纬度
                Location.INSTANCE.setLongitude(String.format("%.2f", aMapLocation.getLongitude()));//获取经度
                aMapLocation.getAccuracy();//获取精度信息
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = new Date(aMapLocation.getTime());
                df.format(date);//定位时间
            } else {
                //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                Log.e("AmapError", "location Error, ErrCode:"
                        + aMapLocation.getErrorCode() + ", errInfo:"
                        + aMapLocation.getErrorInfo());
            }
        }
    }

}
