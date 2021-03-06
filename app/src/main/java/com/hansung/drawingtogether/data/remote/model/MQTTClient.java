package com.hansung.drawingtogether.data.remote.model;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.twocoffeesoneteam.glidetovectoryou.GlideToVectorYou;
import com.hansung.drawingtogether.databinding.FragmentDrawingBinding;
import com.hansung.drawingtogether.monitoring.ComponentCount;
import com.hansung.drawingtogether.monitoring.Velocity;
import com.hansung.drawingtogether.view.WarpingControlView;
import com.hansung.drawingtogether.view.drawing.AudioPlayThread;
import com.hansung.drawingtogether.view.drawing.AutoDraw;
import com.hansung.drawingtogether.view.drawing.ComponentType;
import com.hansung.drawingtogether.view.drawing.DrawingComponent;
import com.hansung.drawingtogether.view.drawing.DrawingEditor;
import com.hansung.drawingtogether.view.drawing.DrawingFragment;
import com.hansung.drawingtogether.view.drawing.DrawingItem;
import com.hansung.drawingtogether.view.drawing.DrawingView;
import com.hansung.drawingtogether.view.drawing.DrawingViewModel;
import com.hansung.drawingtogether.view.drawing.EraserTask;
import com.hansung.drawingtogether.view.drawing.JSONParser;
import com.hansung.drawingtogether.view.drawing.Mode;
import com.hansung.drawingtogether.view.drawing.MqttMessageFormat;
import com.hansung.drawingtogether.view.drawing.Text;
import com.hansung.drawingtogether.view.drawing.TextAttribute;
import com.hansung.drawingtogether.view.drawing.TextMode;
import com.hansung.drawingtogether.view.main.AliveMessage;
import com.hansung.drawingtogether.view.main.AutoDrawMessage;
import com.hansung.drawingtogether.view.main.CloseMessage;
import com.hansung.drawingtogether.view.main.ExitMessage;
import com.hansung.drawingtogether.view.main.JoinAckMessage;
import com.hansung.drawingtogether.view.main.JoinMessage;
import com.hansung.drawingtogether.view.main.MQTTSettingData;
import com.hansung.drawingtogether.view.main.MainActivity;
import com.hansung.drawingtogether.view.main.WarpingMessage;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import com.hansung.drawingtogether.monitoring.Velocity;

import lombok.Getter;

@Getter
public enum MQTTClient {
    INSTANCE;

    /* MQTT ?????? ?????? */
    private MqttClient client;
    private MqttClient client2;  // messageArrived ?????? ???????????? publish ?????? MqttClient ??????

    private String BROKER_ADDRESS;
    private int qos = 2;

    private MQTTSettingData data = MQTTSettingData.getInstance();
    private MqttConnectOptions connOpts;

    /* TOPIC */
    private String topic;
    private String topic_join;
    private String topic_exit;
    private String topic_close;
    private String topic_data;
    private String topic_mid;
    private String topic_audio;
    private String topic_image;
    private String topic_alive;
    private String topic_monitoring;

    private boolean master;
    private boolean isMid = true;

    private String masterName;
    private String myName;

    private JSONParser parser = JSONParser.getInstance();
    private DrawingEditor de = DrawingEditor.getInstance();
    private Logger logger = Logger.getInstance();

    private DrawingViewModel drawingViewModel;
    private DrawingFragment drawingFragment;
    private FragmentDrawingBinding binding;
    private DrawingView drawingView;

    private List<User> userList = new ArrayList<>(100);  // Member List
    private List<AudioPlayThread> audioPlayThreadList = new ArrayList<>(100);

    private Thread th;
    private int aliveLimitCount = 5;

    private int totalMoveX = 0;
    private int totalMoveY = 0;

    private int savedFileCount = 0;

    private boolean exitCompleteFlag = false;

    private ProgressDialog progressDialog;

    // fixme nayeon for monitoring
    /* ???????????? ?????? ?????? */
    private ComponentCount componentCount;
    private Thread monitoringThread;

    // [Key] UUID [Value] Velocity
//    public static Vector<Velocity> receiveTimeList = new Vector<Velocity>();  // ???????????? ??????????????? ?????? ?????? ?????????
//    public static Vector<Velocity> displayTimeList = new Vector<Velocity>();  // ????????? ??????????????? ?????? ?????? ?????????
//    public static Vector<Velocity> deliveryTimeList = new Vector<Velocity>(); // ?????? ??????????????? ???????????? ??????????????? ?????? ?????? ?????????

    public static MQTTClient getInstance() {
        return INSTANCE;
    }

    public void init(String topic, String name, boolean master, DrawingViewModel drawingViewModel,
                     String ip, String port, String masterName) {
        connect(ip, port, topic, name);

        this.master = master;
        this.topic = topic;
        this.myName = name;
        this.masterName = masterName;

        userList.clear();
        audioPlayThreadList.clear();

        if (!isMaster()) {
            User mUser = new User(masterName, 0, MotionEvent.ACTION_UP, false);
            userList.add(mUser);

            /* ???????????? PlayThread ?????? */
            AudioPlayThread audioPlayThread = new AudioPlayThread();
            audioPlayThread.setUserName(masterName);
            audioPlayThread.setBufferUnitSize(4);
            audioPlayThread.start();
            audioPlayThreadList.add(audioPlayThread);
            MyLog.i("Audio", masterName + " ?????? ??? : " + audioPlayThreadList.size());
        }

        User user = new User(myName, 0, MotionEvent.ACTION_UP, false);
        userList.add(user); // ??????????????? ????????? ???????????? ??? ?????? ??????

        topic_join = this.topic + "_join";
        topic_exit = this.topic + "_exit";
        topic_close = this.topic + "_close";
        topic_data = this.topic + "_data";
        topic_mid = this.topic + "_mid";
        topic_alive = this.topic + "_alive";
        topic_audio = this.topic + "_audio";
        topic_image = this.topic + "_image";

        topic_monitoring = "monitoring";

        this.drawingViewModel = drawingViewModel;
        this.drawingViewModel.setUserNum(userList.size());
        this.drawingViewModel.setUserPrint(userPrint());

        //this.usersActionMap = new HashMap<>();
        de.setMyUsername(name);
        isMid = true;
    }

    public void connect(String ip, String port, String topic, String name) { // client id = "*name_topic_android"
        try {
            BROKER_ADDRESS = "tcp://" + ip + ":" + port;

            /* ????????? ???????????? ???????????? ?????????????????? ?????? */
            /* ????????? ????????? ???????????? client id??? ?????? */
            client = new MqttClient(BROKER_ADDRESS, ("*" + name + "_" + topic + "_Android"), new MemoryPersistence());
            client2 = new MqttClient(BROKER_ADDRESS, MqttClient.generateClientId(), new MemoryPersistence());

            connOpts = new MqttConnectOptions();

            connOpts.setCleanSession(true);
            connOpts.setKeepAliveInterval(1000);
            connOpts.setMaxInflight(5000);

            /* ??????????????? ????????? ?????????  ???????????? ????????? ????????? ?????? */
            connOpts.setAutomaticReconnect(true);

            client.connect(connOpts);
            client2.connect(connOpts);
            MyLog.i("mqtt", "CONNECT");

            String currentClientId = client.getClientId();
            MyLog.i("mqtt", "Client ID: " + currentClientId);
        } catch (MqttException e) {
            e.printStackTrace();
            showTimerAlertDialog("????????? ?????? ??????", "?????? ???????????? ???????????????");
        }
    }

    public void subscribe(String newTopic) {
        try {
            client.subscribe(newTopic, this.qos);
            MyLog.i("mqtt", "SUBSCRIBE topic: " + newTopic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // fixme nayeon for performance
    /*
    public void monitoringClientSetting(MqttClient client, String topic) {
        try {

            // ????????? ???????????? ???????????? ?????????????????? ??????
            // ????????? ????????? ???????????? client id??? ??????

            MqttConnectOptions connOpts = new MqttConnectOptions();

            connOpts.setCleanSession(true);
            connOpts.setKeepAliveInterval(1000);
            connOpts.setMaxInflight(5000);   //?

            connOpts.setAutomaticReconnect(true);

            client.connect(connOpts);

            // subscribeAllTopics(client);

            // clients.add(client);

            MyLog.e("kkankkan", topic + " subscribe");
            MyLog.i("mqtt", "SUBSCRIBE topic: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
     */

    // fixme nayeon for performance
    /*
    public void subscribeAllTopics(MqttClient client) {
        try {
            client.subscribe(topic_join);
            client.subscribe(topic_exit);
            client.subscribe(topic_close);
            client.subscribe(topic_data);
            client.subscribe(topic_mid);
            client.subscribe(topic_image);
            client.subscribe(topic_alive);
        }catch(MqttException e) { e.printStackTrace(); }

    }
     */

    public void publish(String newTopic, String payload) {
        try {
            client.publish(newTopic, new MqttMessage(payload.getBytes()));
        } catch (MqttException e) {
            e.printStackTrace();
            showTimerAlertDialog("????????? ?????? ??????", "?????? ???????????? ???????????????");
        }
    }

    public void publish(String newTopic, byte[] payload) {
        try {
            client.publish(newTopic, new MqttMessage(payload));
        } catch (MqttException e) {
            e.printStackTrace();
            /* ????????? On ???????????? Exit Or Close??? ?????? Record Thread Interrupt ?????? */
            /*if (drawingViewModel.getRecThread().isAlive()) {  //fixme minj
                drawingViewModel.getRecThread().interrupt();
            }*/
            drawingViewModel.getRecThread().getExecutor().shutdown();
        }
    }

    public void subscribeAllTopics() {
        subscribe(topic_join);
        subscribe(topic_exit);
        subscribe(topic_close);
        subscribe(topic_data);
        subscribe(topic_mid);
        subscribe(topic_image);
        subscribe(topic_alive);
    }

    /* ????????? ?????? ?????? ?????? ??? ?????? */
    public void exitTask() {
        try {
            MyLog.i("ExitTask", "ExitTask ??????");

            th.interrupt(); // Alive Thread Interrupt

            // fixme nayeon for monitoring
            if(isMaster())
                monitoringThread.interrupt();

            if (isMaster()) {
                CloseMessage closeMessage = new CloseMessage(myName);
                MqttMessageFormat messageFormat = new MqttMessageFormat(closeMessage);
                publish(topic_close, JSONParser.getInstance().jsonWrite(messageFormat));

                MyLog.i("ExitTask", "Master Close Publish");
            } else {
                ExitMessage exitMessage = new ExitMessage(myName);
                MqttMessageFormat messageFormat = new MqttMessageFormat(exitMessage);
                publish(topic_exit, JSONParser.getInstance().jsonWrite(messageFormat));

                MyLog.i("ExitTask", "Participant Exit Publish");
            }

            /* UNSUBSCRIBE */
            client.unsubscribe(topic_join);
            client.unsubscribe(topic_exit);
            client.unsubscribe(topic_close);
            client.unsubscribe(topic_data);
            client.unsubscribe(topic_image);
            client.unsubscribe(topic_mid);
            client.unsubscribe(topic_alive);

            isMid = true;
            exitCompleteFlag = true;

            MyLog.i("ExitTask", "ExitTask ??????");
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /* ?????? ????????? ?????? */
    public String userPrint() {
        String names = "";
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).getName().equals(myName) && isMaster())
                names += userList.get(i).getName() + " ??? (???)\n";
            else if (userList.get(i).getName().equals(masterName))
                names += userList.get(i).getName() + " ???\n";
            else if (userList.get(i).getName().equals(myName) && !isMaster())
                names += userList.get(i).getName() + " (???)\n";
            else
                names += userList.get(i).getName() + "\n";
        }
        return names;
    }

    public void setCallback() {
        client.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    MyLog.i("modified mqtt", "RECONNECT");
                    subscribeAllTopics();

                    // fixme nayeon for monitoring
                    if(isMaster())
                        monitoringThread.notify();


                    /* ????????? On(????????? subscribe ???)???????????? ?????? subscribe */
                    if (drawingViewModel.isSpeakerFlag())
                        subscribe(topic_audio);
                } else {
                    MyLog.i("modified mqtt", "CONNECT");
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                MyLog.i("modified mqtt", "CONNECTION LOST : " + cause.getCause().toString());
                cause.printStackTrace();

                // fixme nayeon for monitoring
                try {
                    if(isMaster())
                        monitoringThread.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void messageArrived(String newTopic, MqttMessage message) throws Exception {

                // fixme nayeon for performance
//                if(!newTopic.equals(topic_image)) {
//                    MqttMessageFormat mmf = (MqttMessageFormat) parser.jsonReader(new String(message.getPayload()));
//
//                    if(isMaster()) {
//                        Log.e("performance", "component count = " + de.getDrawingComponents().size());
//                    }
//
//
//                    if (isMaster() && mmf.getAction() != null && mmf.getAction() == MotionEvent.ACTION_MOVE
//                            && mmf.getType().equals(ComponentType.STROKE)) { // ???????????? STROKE ??? MOVE ???????????? ?????? ???????????? ????????? ??????
//                        if (mmf.getUsername().equals(myName)) { // ?????? ????????? ?????? ???????????? ?????? [???????????? ????????? ?????? ?????? ??????]
//                            System.out.println("here");
//                            (receiveTimeList.lastElement()).calcTime(System.currentTimeMillis(), message.getPayload().length);
//                            // printReceiveTimeList();
//                        }
//                        else if (!mmf.getUsername().equals(myName)) { // ?????? ????????? ?????? ???????????? ?????? [????????? ????????? ?????? ??????]
//                            displayTimeList.add(new Velocity(System.currentTimeMillis(), de.getDrawingComponents().size(), message.getPayload().length));
//                        }
//                    }
//
//                }

                /* TOPIC_JOIN */
                if (newTopic.equals(topic_join)) {

                    String msg = new String(message.getPayload());
                    MqttMessageFormat mqttMessageFormat = (MqttMessageFormat) parser.jsonReader(msg);

                    JoinMessage joinMessage = mqttMessageFormat.getJoinMessage();
                    JoinAckMessage joinAckMessage = mqttMessageFormat.getJoinAckMessage();

                    /* ??? ???????????? ?????? ???????????? ?????? */
                    if (joinMessage != null) {
                        Log.i("JoinMessage", "JoinMessage Arrived");

                        String name = joinMessage.getName();

                        if (!name.equals(myName)) {
                            if (!isContainsUserList(name)) {

                                /* ??? ????????? ?????? ?????? ?????? ?????? */
                                /* ??? ???????????? ????????? ?????? ???????????? ?????? */
                                User user = new User(name, 0, MotionEvent.ACTION_UP, false);
                                userList.add(user);

                                /* ?????? ?????? ?????? */
                                /* JoinAckMessage ?????? */
                                if (!master) {
                                    JoinAckMessage joinAckMsg = new JoinAckMessage(myName, name);
                                    MqttMessageFormat msgFormat = new MqttMessageFormat(joinAckMsg);
                                    client2.publish(topic_join, new MqttMessage(parser.jsonWrite(msgFormat).getBytes()));
                                }

                                /* ?????? ???????????? Play Thread ?????? */
                                AudioPlayThread audioPlayThread = new AudioPlayThread();
                                audioPlayThread.setUserName(name);
                                audioPlayThread.setBufferUnitSize(4);
                                if (drawingViewModel.isSpeakerFlag())
                                    audioPlayThread.setFlag(true);
                                audioPlayThread.start();
                                audioPlayThreadList.add(audioPlayThread);
                                MyLog.i("Audio", name + " ?????? ??? : " + audioPlayThreadList.size());

                                /* ?????? ???????????? ??????????????? ???????????? ????????? ?????? */
                                /* ????????? ??????????????? ?????? ????????? ?????? */
                                Log.e("mid", "before mid enter true");
                                de.setMidEntered(true);
                                Log.e("mid", "after mid enter true, isMidEntered = " + de.isMidEntered());


                                //if (de.getCurrentMode() == Mode.DRAW) {  // current mode ??? DRAW ??????, ????????? ????????? component ????????? ????????? touch intercept   // todo ?????? ??????????????? intercept ????????? ??????
                                    de.setIntercept(true);
                                    if(!getDrawingView().isMovable()) {
                                        getDrawingView().setIntercept(true);
                                    }
                                //}

                                setToastMsg("[ " + name + " ] ?????? ?????????????????????");

                                /* ?????? ????????? ?????? */
                                drawingViewModel.setUserNum(userList.size());
                                drawingViewModel.setUserPrint(userPrint());
                            }
                            /* master ?????? */
                            if (master) {
                                if (isUsersActionUp(name) && !isTextInUse()) {
                                    MyLog.i("text", "check text in use");
                                    JoinAckMessage joinAckMsgMaster = new JoinAckMessage(myName, name);

                                    /* ???????????? ????????? ???????????? ????????? ?????? */
                                    /* ????????? ???????????? MqttMessageFormat, ????????? ???????????? binary??? publish */
                                    MqttMessageFormat messageFormat = new MqttMessageFormat(joinAckMsgMaster, de.getDrawingComponents(), de.getTexts(), de.getHistory(), de.getUndoArray(), de.getRemovedComponentId(), de.getMaxComponentId(), de.getMaxTextId(), de.getAutoDrawList());
                                    String json = parser.jsonWrite(messageFormat);

                                    // fixme nayeon for performance
                                    // deliveryTimeList.add(new Velocity(System.currentTimeMillis(), name, json.getBytes().length, de.getDrawingComponents().size()));

                                    client2.publish(topic_join, new MqttMessage(json.getBytes()));

                                    if (de.getBackgroundImage() != null) {
                                        /* ?????? ???????????? ????????? ???????????? ???????????? ?????? */
                                        byte[] backgroundImage = de.bitmapToByteArray(((WarpingControlView)MQTTClient.getInstance().getBinding().backgroundView).getImage());
                                        client2.publish(topic_image, new MqttMessage(backgroundImage));
                                    }

//                                    for (int i = 0; i < de.getAutoDrawImageList().size(); i++) {
//                                        String url = de.getAutoDrawImageList().get(i);
//                                        ImageView view = de.getAutoDrawImageViewList().get(i);
//                                        AutoDrawMessage autoDrawMessage = new AutoDrawMessage(data.getName(), url, view.getX(), view.getY(), de.getMyCanvasWidth(), de.getMyCanvasHeight());
//                                        MqttMessageFormat messageFormat2 = new MqttMessageFormat(de.getMyUsername(), de.getCurrentMode(), de.getCurrentType(), autoDrawMessage);
//                                        String json2 = parser.jsonWrite(messageFormat2);
////                                        client2.publish(topic_data, new MqttMessage(json2.getBytes()));
//                                    }

                                    setToastMsg("[ " + name + " ] ????????? ????????? ????????? ??????????????????");

                                } else {
                                    MqttMessageFormat messageFormat = new MqttMessageFormat(new JoinMessage(name));
                                    client2.publish(topic_join, new MqttMessage(parser.jsonWrite(messageFormat).getBytes()));
                                    MyLog.i("master republish name", topic_join);
                                }
                            }
                        }

                    }
                    /* master ?????? ?????? ????????? ?????? ???????????? ?????? */
                    else if (joinAckMessage != null) {

                        /* ??? ????????? ?????? */
                        Log.e("joinAckMessage", "JoinAckMessage Arrived");

                        String name = joinAckMessage.getName();
                        String target = joinAckMessage.getTarget();

                        if (target.equals(myName)) {
                            if (name.equals(masterName)) {
                                /* master??? ?????? ???????????? ?????? */

                                /* ???????????? ????????? ???????????? ???????????? ?????? */
                                /* ????????? ?????? ??????????????? ?????? ????????? ?????? */
                                de.setDrawingComponents(mqttMessageFormat.getDrawingComponents());
                                de.setHistory(mqttMessageFormat.getHistory());
                                de.setUndoArray(mqttMessageFormat.getUndoArray());
                                de.setRemovedComponentId(mqttMessageFormat.getRemovedComponentId());
                                de.setAutoDrawList(mqttMessageFormat.getAutoDrawList());

                                de.setTexts(mqttMessageFormat.getTexts());
                                if (mqttMessageFormat.getBitmapByteArray() != null) {
                                    de.byteArrayToBitmap(mqttMessageFormat.getBitmapByteArray());
                                }

                                // ????????? ??????
                                de.setMaxComponentId(mqttMessageFormat.getMaxComponentId());
                                // de.setTextId(mqttMessageFormat.getMaxTextId()); // ????????? ???????????? "???????????????-textIdCount" ????????? textIdCount ??? ????????? ??????
                                MyLog.i("drawing", "component id = " + mqttMessageFormat.getMaxComponentId() + ", text id = " + mqttMessageFormat.getMaxTextId());

                                client2.publish(topic_mid, new MqttMessage(JSONParser.getInstance().jsonWrite(new MqttMessageFormat(myName, Mode.MID)).getBytes()));
                            }
                            else if (!isContainsUserList(name)) {
                                /* ?????? ????????? ?????? ???????????? ?????? */
                                /* ????????? ????????? ?????? ???????????? ?????? */
                                User user = new User(name, 0, MotionEvent.ACTION_UP, false);
                                userList.add(user);

                                /* ?????? ???????????? Play Thread ?????? */
                                AudioPlayThread audioPlayThread = new AudioPlayThread();
                                audioPlayThread.setUserName(name);
                                audioPlayThread.setBufferUnitSize(4);
                                audioPlayThread.start();
                                audioPlayThreadList.add(audioPlayThread);
                                MyLog.i("audio", name + " ?????? ??? : " + audioPlayThreadList.size());

                                /* ?????? ????????? ?????? */
                                drawingViewModel.setUserNum(userList.size());
                                drawingViewModel.setUserPrint(userPrint());
                            }
                        }
                    }
                }

                /* TOPIC_EXIT */
                if (newTopic.equals(topic_exit)) {

                    String msg = new String(message.getPayload());
                    MqttMessageFormat mqttMessageFormat = (MqttMessageFormat) parser.jsonReader(msg);
                    ExitMessage exitMessage = mqttMessageFormat.getExitMessage();
                    String name = exitMessage.getName();

                    if (!myName.equals(name)) {

                        /* ?????? ??????????????? ?????? ?????? */
                        for (int i=0; i<userList.size(); i++) {
                            if (userList.get(i).getName().equals(name)) {
                                userList.remove(i);

                                /* ?????? ????????? ?????? */
                                drawingViewModel.setUserNum(userList.size());
                                drawingViewModel.setUserPrint(userPrint());

                                setToastMsg("[ " + name + " ] ?????? ??????????????????");
                                break;
                            }
                        }

                        /* ?????? ????????? Play Thread Interrupt, PlayThreadList?????? ?????? */
                        for (int i=0; i<audioPlayThreadList.size(); i++) {
                            if (audioPlayThreadList.get(i).getUserName().equals(name)) {
                                audioPlayThreadList.get(i).setFlag(false);
                                audioPlayThreadList.get(i).getBuffer().clear();
                                audioPlayThreadList.get(i).stopPlaying();
                                //audioPlayThreadList.get(i).interrupt(); //fixme minj
                                audioPlayThreadList.get(i).getExecutor().shutdown();
                                MyLog.i("Audio", name + " remove ??? : " + audioPlayThreadList.size());
                                audioPlayThreadList.remove(i);
                                MyLog.i("Audio", name + " remove ??? : " + audioPlayThreadList.size());
                            }
                        }
                    }
                }

                /* TOPIC_CLOSE */
                if (newTopic.equals(topic_close)) {

                    String msg = new String(message.getPayload());
                    MqttMessageFormat mqttMessageFormat = (MqttMessageFormat) parser.jsonReader(msg);
                    CloseMessage closeMessage = mqttMessageFormat.getCloseMessage();

                    String name = closeMessage.getName();

                    /* ????????? ?????? */
                    if (!name.equals(myName)) {
                        showExitAlertDialog("???????????? ???????????? ?????????????????????");
                    }
                }

                /* TOPIC_ALIVE */
                if (newTopic.equals(topic_alive)) {

                    String msg = new String(message.getPayload());
                    MqttMessageFormat mqttMessageFormat = (MqttMessageFormat) parser.jsonReader(msg);
                    AliveMessage aliveMessage = mqttMessageFormat.getAliveMessage();
                    String name = aliveMessage.getName();

                    if (myName.equals(name)) {
                        Iterator<User> iterator = userList.iterator();
                        while (iterator.hasNext()) {
                            User user = iterator.next();

                            if (!user.getName().equals(myName)) {
                                user.setCount(user.getCount() + 1);

                                if (user.getCount() == aliveLimitCount && user.getName().equals(masterName)) {
                                    showExitAlertDialog("????????? ????????? ???????????????. ???????????? ???????????????.");
                                }
                                else if (user.getCount() == aliveLimitCount) {
                                    iterator.remove();
                                    drawingViewModel.setUserNum(userList.size());
                                    drawingViewModel.setUserPrint(userPrint());
                                    setToastMsg("[ " + user.getName() + " ] ??? ????????? ???????????????");
                                }
                            }
                        }
                    } else {
                        for (User user : userList) {
                            if (user.getName().equals(name)) {
                                user.setCount(0);
                                break;
                            }
                        }
                    }
                }

                /* TOPIC_DATA */
                if (newTopic.equals(topic_data) && de.getMainBitmap() != null) {
                    String msg = new String(message.getPayload());

                    //MyLog.i("drawMsg", msg);

                    MqttMessageFormat messageFormat = (MqttMessageFormat) parser.jsonReader(msg);

                    /* ?????? ???????????? ???????????? ??? ?????? */
                    if(de.isMidEntered() && (messageFormat.getAction() != null && messageFormat.getAction() != MotionEvent.ACTION_UP)) { // getAction == null
                        if(/*getDrawingView().isIntercept() || */(de.isIntercept() && (messageFormat.getAction() != null && messageFormat.getAction() == MotionEvent.ACTION_DOWN)) || (de.getCurrentComponent(messageFormat.getUsersComponentId()) == null))
                            return;
                    }


                    // fixme nayeon for monitoring
                    /* ???????????? ?????? (???????????? ?????? ?????????) Only Master */
                    if(isMaster()) {
                        MyLog.i("> monitoring", "before check component count");
                        MyLog.i("> monitoring", "mode = " + messageFormat.getMode() + ", type = " + messageFormat.getType()
                                + ", text mode = " + messageFormat.getTextMode());

                        /* ???????????? ?????? ?????? */
                        if ((messageFormat.getAction() != null && messageFormat.getAction() == MotionEvent.ACTION_DOWN)
                                || messageFormat.getMode() == Mode.TEXT || messageFormat.getMode() == Mode.ERASE) {
                            MyLog.i("< monitoring", "mode = " + messageFormat.getMode() + ", type = " + messageFormat.getType()
                                    + ", text mode = " + messageFormat.getTextMode());
                            checkComponentCount(messageFormat.getMode(), messageFormat.getType(), messageFormat.getTextMode());
                        }

                        MyLog.i("< monitoring", "after check component count");
                    }

                    /* ???????????? ?????? */
                    if (messageFormat.getMode() == Mode.TEXT) {  // TEXT ????????? ??????, username??? ?????? ????????? task ??????
                        if (!messageFormat.getUsername().equals(de.getMyUsername())) {
//                            MyLog.i("drawing", "username = " + messageFormat.getUsername() + ", text id = " + messageFormat.getTextAttr().getId() + ", mode = " + messageFormat.getMode() + ", text mode = " + messageFormat.getTextMode());
                            new TextTask().execute(messageFormat);
                        }
                    } else {
                        //new DrawingTask().execute(messageFormat);
                        new DrawingTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, messageFormat);
                    }
                }

                /* TOPIC_MID */
                if (newTopic.equals(topic_mid)) {
                    String msg = new String(message.getPayload());
                    MqttMessageFormat messageFormat = (MqttMessageFormat) parser.jsonReader(msg);

                    MyLog.i("mqtt", "isMid=" + isMid() + ", " + de.getMyUsername());
                    if (isMid && messageFormat.getUsername().equals(de.getMyUsername())) {
                        isMid = false;
                        MyLog.i("mqtt", "mid username=" + messageFormat.getUsername());
                        new MidTask().execute();
                    }

                    de.setIntercept(false);
                    MyLog.i("mqtt", "set intercept false");

                    /* ?????? ???????????? topic_mid ??? ????????? ???????????? */
                    /* ??? ?????? ?????????????????? ?????? ????????? ?????? ?????? ??? */
                    de.setMidEntered(false);
                }

                /* TOPIC_AUDIO */
                if (newTopic.equals(topic_audio)) {
                    byte[] audioMessage = message.getPayload();

                    byte[] nameByte = Arrays.copyOfRange(audioMessage, 10000, audioMessage.length);
                    String name = new String(nameByte);

                    if (myName.equals(name)) return; // ????????? ???????????? ?????? ??????

                    byte[] audioData = Arrays.copyOfRange(audioMessage, 0, audioMessage.length - nameByte.length);

                    /* username??? ???????????? ?????? PlayThread??? ????????? ?????? ????????? ????????? ?????? */
                    for (AudioPlayThread audioPlayThread : audioPlayThreadList) {
                        if (audioPlayThread.getUserName().equals(name)) {
                            if (audioPlayThread.getBuffer().size() >= 5) {
                                audioPlayThread.getBuffer().clear();
                            }
                            audioPlayThread.getBuffer().add(audioData);
                            break;
                        }
                    }
                }

                /* TOPIC_IMAGE */
                if (newTopic.equals(topic_image)) {
                    byte[] imageData = message.getPayload();
                    de.setBackgroundImage(de.byteArrayToBitmap(imageData));

                    /* ????????? ?????? ??? ???????????? ????????? ?????? WarpingControlView??? backgroundview??? ??????, ???????????? ?????????????????? ?????? */
                    binding.backgroundView.setCancel(true);
                    binding.backgroundView.setImage(de.getBackgroundImage());
                    MyLog.i("Image", "set image");

                    // fixme nayeon for monitoring
                    if(isMaster()) { // ????????? ????????? ?????? ????????? (????????????)
                        componentCount.increaseImage();
                    }

                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }

    /* ?????? ???????????? ???????????? ???????????? ?????? */
    public boolean isContainsUserList(String username) {
        for (int i = 0; i < userList.size(); i++) {
            if (userList.get(i).getName().equals(username))
                return true;
        }
        return false;
    }

    public void updateUsersAction(String username, int action) {
        for(User user : userList) {
            if(user.getName().equals(username)) {
                user.setAction(action);
                if(action == MotionEvent.ACTION_UP)
                    MyLog.i("intercept", username + " UP");
            }
        }
    }

    public boolean isUsersActionUp(String username) {
        /*if(!usersActionMap.containsValue(MotionEvent.ACTION_DOWN) && !usersActionMap.containsValue(MotionEvent.ACTION_MOVE))
            return true;
        else
            return false;*/

        for (User user : userList) {
            try {
                if (!user.getName().equals(username) && user.getAction() != MotionEvent.ACTION_UP)
                    return false;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return false;
            }
        }
        String str = "";
        for(User user : userList) {
            if (!user.getName().equals(username)) {
                str += "[" + user.getName() + ", " + MotionEvent.actionToString(user.getAction()) + "] ";
            }
        }
        MyLog.i("drawing", "users action = " + str);

        return true;
    }

    public boolean isTextInUse() {
        MyLog.i("text", "inTextInUse func");

        for (Text t : de.getTexts()) {
            if (/*t.getTextAttribute().getUsername() != null*/ t.getTextAttribute().isDragging()) {
                Log.e("text", "text in use id = " + t.getTextAttribute().getId());
                return true;
            }
        }
        return false;
    }

    // fixme nayeon for monitoring
    public void checkComponentCount(Mode mode, ComponentType type, TextMode textMode) {
        Log.i("monitoring", "execute check component count func.");

        if(mode == Mode.TEXT && textMode == TextMode.CREATE) {
            //Log.e("monitoring", "check component count func. text count increase.");

            componentCount.increaseText();
            return;
        }

        if(mode != Mode.DRAW) {
            //Log.e("monitoring", "check component count func. mode is not DRAW");
            return;
        }
        //Log.e("monitoring", "check component count func. mode is DRAW");


        switch (type) {
            case STROKE:
                componentCount.increaseStroke();
                break;
            case RECT:
                componentCount.increaseRect();
                break;
            case OVAL:
                componentCount.increaseOval();
                break;
        }
    }

    public void doInBack() {
        th.interrupt();

        // fixme nayeon for monitoring
        if(isMaster())
            monitoringThread.interrupt();


        isMid = true;
        de.removeAllDrawingData();
        drawingViewModel.back();
    }

    public void showTimerAlertDialog(final String title, final String message) {
        final MainActivity mainActivity = (MainActivity) MainActivity.context;
        Objects.requireNonNull(mainActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(mainActivity)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MyLog.d("drawing", "dialog onclick");
                                MyLog.d("button", "timer dialog ok button click");
                                doInBack();
                                dialog.dismiss();
                            }
                        })
                        .create();

                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    private static final int AUTO_DISMISS_MILLIS = 6000;

                    @Override
                    public void onShow(final DialogInterface dialog) {
                        final Button defaultButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                        final CharSequence negativeButtonText = defaultButton.getText();
                        new CountDownTimer(AUTO_DISMISS_MILLIS, 100) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                defaultButton.setText(String.format(
                                        Locale.getDefault(), "%s (%d)",
                                        negativeButtonText,
                                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) + 1 //add one so it never displays zero
                                ));
                            }

                            @Override
                            public void onFinish() {
                                if (((AlertDialog) dialog).isShowing()) {
                                    doInBack();
                                    dialog.dismiss();
                                }
                            }
                        }.start();
                    }
                });
                dialog.show();
                MyLog.i("mqtt", "timer dialog show");
            }
        });
    }

    public void showExitAlertDialog(final String message) {

        final MainActivity mainActivity = (MainActivity)MainActivity.context;
        Objects.requireNonNull(mainActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(mainActivity)
                        .setTitle("????????? ??????")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MyLog.d("button", "exit dialog ok button click");
                                if (client.isConnected()) {
                                    exitTask();

                                    // fixme nayeon for performance
//                                    if(isMaster()) {
//                                        MonitoringDataWriter.getInstance().write();
//                                    }

                                }
                                if (progressDialog.isShowing())
                                    progressDialog.dismiss();
                                drawingViewModel.back();
                            }
                        })
                        .setNeutralButton("?????? ??? ??????", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(!exitCompleteFlag) drawingViewModel.clickSave();
                                if (client.isConnected()) {
                                    exitTask();

                                    // fixme nayeon for performance
//                                    if(isMaster()) {
//                                        MonitoringDataWriter.getInstance().write();
//                                    }

                                }
                                if (progressDialog.isShowing())
                                    progressDialog.dismiss();
                                drawingViewModel.back();
                            }
                        })
                        .create();
                dialog.show();
                MyLog.i("mqtt", "exit dialog show");
            }
        });
    }

    public void setToastMsg(final String message) {
        final MainActivity mainActivity = (MainActivity)MainActivity.context;
        Objects.requireNonNull(mainActivity).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Objects.requireNonNull(mainActivity).getApplicationContext(), message, Toast.LENGTH_SHORT).show();

            }
        });
    }

    /* SETTER */

    public void setDrawingFragment(DrawingFragment drawingFragment) {
        this.drawingFragment = drawingFragment;
        this.binding = drawingFragment.getBinding();
        this.drawingView = this.binding.drawingView;
    }

    public void setProgressDialog(ProgressDialog progressDialog) {
        this.progressDialog = progressDialog;
    }

    public void setThread(Thread th) {
        this.th = th;
    }


    public void setIsMid(boolean mid) {
        this.isMid = mid;
    }

    public void setAliveLimitCount(int aliveLimitCount) {
        this.aliveLimitCount = aliveLimitCount;
    }

    public int getSavedFileCount() { return ++savedFileCount; }

    public void setTotalMovePoint(int x, int y) {
        this.totalMoveX = x;
        this.totalMoveY = y;
    }

    /* monitoring variable setting function */
    // fixme nayeon for monitoring
    public void setMonitoringThread(Thread monitoringThread) {
        this.monitoringThread = monitoringThread;
    }

    // fixme nayeon for monitoring
    public void setComponentCount(ComponentCount componentCount) { this.componentCount = componentCount; }

    // fixme nayeon for performance
    /* monitoring print function */
    /*
    public void printReceiveTimeList() {
        System.out.println("-------------------- Receive Time List --------------------");

        for(int i=0; i<receiveTimeList.size(); i++)
            System.out.println(i + ". " + receiveTimeList.get(i).toString());
    }

    public void printDisplayTimeList() {
        System.out.println("-------------------- Display Time List --------------------");

        for(int i=0; i<displayTimeList.size(); i++)
            System.out.println(i + ". " + displayTimeList.get(i).toString());
    }

    public void printDeliveryTimeList() {
        System.out.println("-------------------- Delivery Time List --------------------");

        for(int i=0; i<deliveryTimeList.size(); i++)
            System.out.println(i + ". " + deliveryTimeList.get(i).toString());
    }
     */

    static class DrawingTask extends AsyncTask<MqttMessageFormat, MqttMessageFormat, Void> {
        private String username;
        private int action;
        private DrawingComponent dComponent;
        private Point point;
        private MQTTClient client = MQTTClient.getInstance();
        private DrawingEditor de = DrawingEditor.getInstance();
        private float myCanvasWidth = client.getDrawingView().getCanvasWidth();
        private float myCanvasHeight = client.getDrawingView().getCanvasHeight();
        private WarpingMessage warpingMessage;

        @Override
        protected Void doInBackground(MqttMessageFormat... messages) {
            MqttMessageFormat message = messages[0];
            this.username = message.getUsername();
            Mode mode = message.getMode();

            if((message.getMode() == null) || (message.getUsername() == null)) {
                return null;
            }

            de.setMyCanvasWidth(myCanvasWidth);
            de.setMyCanvasHeight(myCanvasHeight);

            if(de.getMyUsername().equals(username) && !mode.equals(Mode.DRAW)) { return null; }

            publishProgress(message);

            return null;
        }

        @Override
        protected void onProgressUpdate(MqttMessageFormat... messages) {
            MqttMessageFormat message = messages[0];
            Mode mode = message.getMode();

            switch(mode) {
                case DRAW:
                    try{
                        draw(message);
                    } catch(NullPointerException e) {
                        e.printStackTrace();
                    }


                    // fixme nayeon for performance ( draw point )
//                if (client.isMaster() && /*message.getAction() == MotionEvent.ACTION_MOVE
//                        && messageFormat.getType().equals(ComponentType.STROKE)*/ message.getMode().equals(Mode.DRAW)
//                        && !message.getUsername().equals(de.getMyUsername())) { // ?????? ????????? ?????? ???????????? ?????? [???????????? ????????? ????????? ????????? ?????? ??????]
//
//                    (MQTTClient.displayTimeList.lastElement()).calcTime(System.currentTimeMillis());
//                    //client.printDisplayTimeList();
//                }



                    break;
                case ERASE:
                    MyLog.i("mqtt", "MESSAGE ARRIVED message: username=" + username + ", mode=" + mode.toString() + ", id=" + message.getComponentIds().toString());

                    Vector<Integer> erasedComponentIds = message.getComponentIds();
                    new EraserTask(erasedComponentIds).doNotInBackground();
                    de.clearUndoArray();
                    break;
                case SELECT:
                    if(message.getAction() == null && (de.findDrawingComponentByUsersComponentId(message.getUsersComponentId()) != null)) {
                        de.setDrawingComponentSelected(message.getUsersComponentId(), message.getIsSelected());
                        //de.findDrawingComponentByUsersComponentId(message.getUsersComponentId()).setSelected(message.getIsSelected());
                    }

                    DrawingComponent selectedComponent = de.findDrawingComponentByUsersComponentId(message.getUsersComponentId());
                    if(selectedComponent == null) return;

                    if(message.getAction() == null) {
                    /*if(message.getIsSelected()) {
                        de.clearMyCurrentBitmap();
                        de.drawSelectedComponentBorder(selectedComponent, de.getSelectedBorderColor());
                    } else {
                        de.clearMyCurrentBitmap();
                    }*/
                    } else {
                        switch(message.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                client.setTotalMovePoint(0, 0);
                                MyLog.i("drawing", "other selected true");
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if(message.getMoveSelectPoints().size() == 0) break;
                                for(Point point : message.getMoveSelectPoints()) {
                                    client.setTotalMovePoint(client.getTotalMoveX()+point.x, client.getTotalMoveY()+point.y);
                                    de.moveSelectedComponent(selectedComponent, point.x, point.y);
                                }
                                //de.clearMyCurrentBitmap();
                                de.updateSelectedComponent(selectedComponent);
                                de.clearDrawingBitmap();
                                de.drawAllDrawingComponents();
                                break;
                            case MotionEvent.ACTION_UP:
                                //de.clearMyCurrentBitmap();
                                //de.drawSelectedComponentBorder(selectedComponent, de.getSelectedBorderColor());
                                de.splitPointsOfSelectedComponent(selectedComponent, myCanvasWidth, myCanvasHeight);
                                de.updateSelectedComponent(selectedComponent);
                                de.clearDrawingBitmap();
                                de.drawAllDrawingComponents();

                                if(selectedComponent.clone() != null) {
                                    de.addHistory(new DrawingItem(Mode.SELECT, selectedComponent.clone(), new Point(client.getTotalMoveX(), client.getTotalMoveY())));
                                    MyLog.i("drawing", "history.size()=" + de.getHistory().size() + "id=" + selectedComponent.getId());
                                }
                                de.clearUndoArray();

                                if(de.getCurrentMode() == Mode.SELECT && client.getDrawingView().isSelected()) {
                                    de.setPreSelectedComponentsBitmap();
                                    de.setPostSelectedComponentsBitmap();

                                    de.clearMyCurrentBitmap();
                                    de.drawUnselectedComponents();
                                    de.getSelectedComponent().drawComponent(de.getCurrentCanvas());
                                    de.drawSelectedComponentBorder(de.getSelectedComponent(), de.getMySelectedBorderColor());
                                }

                                MyLog.i("drawing", "other selected finish");
                                break;
                        }
                    }
                    break;
                case CLEAR:
                    MyLog.i("mqtt", "MESSAGE ARRIVED message: username=" + username + ", mode=" + mode.toString());
                    de.clearDrawingComponents();

                    if(client.getBinding().drawingView.isSelected()) {
                        de.deselect(true);
                        //de.clearAllSelectedBitmap();
                    }

                    de.clearTexts();
                    client.getBinding().redoBtn.setEnabled(false);
                    client.getBinding().undoBtn.setEnabled(false);
                    break;
                case CLEAR_BACKGROUND_IMAGE:
                    de.setBackgroundImage(null);
                    de.clearBackgroundImage();
                    break;
                case UNDO:
                    MyLog.i("mqtt", "MESSAGE ARRIVED message: username=" + username + ", mode=" + mode.toString());
                    if(client.getBinding().drawingView.isSelected()) {
                        de.deselect(true);
                        //de.clearAllSelectedBitmap();
                    }

                    if(de.getHistory().size() == 0)
                        return;
                    de.addUndoArray(de.popHistory());
                    if(de.getUndoArray().size() == 1)
                        client.getBinding().redoBtn.setEnabled(true);
                    if(de.getHistory().size() == 0) {
                        client.getBinding().undoBtn.setEnabled(false);
                        de.clearDrawingBitmap();
                        return;
                    }
                    MyLog.i("drawing", "history.size()=" + de.getHistory().size());
                    break;
                case REDO:
                    MyLog.i("mqtt", "MESSAGE ARRIVED message: username=" + username + ", mode=" + mode.toString());
                    if(client.getBinding().drawingView.isSelected()) {
                        de.deselect(true);
                        //de.clearAllSelectedBitmap();
                    }

                    if(de.getUndoArray().size() == 0)
                        return;
                    de.addHistory(de.popUndoArray());
                    if(de.getHistory().size() == 1)
                        client.getBinding().undoBtn.setEnabled(true);
                    if(de.getUndoArray().size() == 0)
                        client.getBinding().redoBtn.setEnabled(false);
                    MyLog.i("drawing", "history.size()=" + de.getHistory().size());
                    break;
                case WARP:
                    this.warpingMessage = message.getWarpingMessage();
//                WarpData data = warpingMessage.getWarpData();
                    ((WarpingControlView)client.getBinding().backgroundView).warping2(warpingMessage);
                    break;
                case AUTODRAW:
                    AutoDrawMessage autoDrawMessage = message.getAutoDrawMessage();
                    ImageView imageView = new ImageView(MainActivity.context);
                    imageView.setLayoutParams(new LinearLayout.LayoutParams(300, 300));

                    Point point = new Point();
                    point.x = (int)(autoDrawMessage.getX() * myCanvasWidth / autoDrawMessage.getWidth());
                    point.y = (int)(autoDrawMessage.getY() * myCanvasHeight / autoDrawMessage.getHeight());

                    imageView.setX(point.x);
                    imageView.setY(point.y);

                    AutoDraw autoDraw = new AutoDraw(myCanvasWidth, myCanvasHeight, point, autoDrawMessage.getUrl());
                    de.addAutoDraw(autoDraw);
                    de.addAutoDrawImageView(imageView);

                    String url = autoDrawMessage.getUrl();
                    client.getBinding().drawingViewContainer.addView(imageView);
                    GlideToVectorYou.init().with(MainActivity.context).load(Uri.parse(url), imageView);
                    break;
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            client.getDrawingView().invalidate();


        }

        private void draw(MqttMessageFormat message) {
            this.action = message.getAction();
            this.point = message.getPoint();

            if(message.getComponent() == null) {
                if(de.getCurrentComponent(message.getUsersComponentId()) == null) return;
                dComponent = de.getCurrentComponent(message.getUsersComponentId());
            } else {
                dComponent = message.getComponent();
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    dComponent.clearPoints();
                    dComponent.setId(de.componentIdCounter());

                    de.addCurrentComponents(dComponent);
                    de.printCurrentComponents("down");

                    client.updateUsersAction(username, action);

                    break;
                case MotionEvent.ACTION_MOVE:
                    if (de.getMyUsername().equals(username)) {
                        for (Point point : message.getMovePoints()) {
                            client.getDrawingView().addPoint(dComponent, point);
                        }
                    } else {
                        dComponent.calculateRatio(myCanvasWidth, myCanvasHeight);

                        //MyLog.i("sendThread", "points[] = " + message.getMovePoints().toString());
                        for(Point point : message.getMovePoints()) {
                            client.getDrawingView().addPointAndDraw(dComponent, point, de.getReceiveCanvas());
                        }
                    }

                    client.updateUsersAction(username, action);

                    break;
                case MotionEvent.ACTION_UP:
                    if(de.getCurrentMode() == Mode.SELECT) {
                        de.addPostSelectedComponent(dComponent);
                    }

                    if(de.getMyUsername().equals(username)) {
                        client.getDrawingView().addPoint(dComponent, point);
                        client.getDrawingView().doInDrawActionUp(dComponent, de.getMyCanvasWidth(), de.getMyCanvasHeight());
                        if(de.isIntercept()) {
                            client.getDrawingView().setIntercept(true);
                            MyLog.i("intercept", "drawingview true (MQTT Client)");
                        }
                    } else {
                        MyLog.i("sendThread", "client draw up");
                        client.getDrawingView().addPointAndDraw(dComponent, point, de.getReceiveCanvas());
                        //client.getDrawingView().redrawShape(dComponent);
                        client.getDrawingView().doInDrawActionUp(dComponent, myCanvasWidth, myCanvasHeight);

                        de.clearCurrentBitmap();
                        de.drawOthersCurrentComponent(dComponent.getUsername());
                        dComponent.drawComponent(de.getMainCanvas());
                    }
                    client.updateUsersAction(username, action);

                    break;
            }

            //if(de.getMyUsername().equals(username)) return;

        }

    }

    static class TextTask extends AsyncTask<MqttMessageFormat, MqttMessageFormat, Void> {
        private MQTTClient client = MQTTClient.getInstance();
        private DrawingEditor de = DrawingEditor.getInstance();

        @Override
        protected Void doInBackground(MqttMessageFormat... messages) {  //changeText()
            MqttMessageFormat message = messages[0];

            TextMode textMode = message.getTextMode();
            TextAttribute textAttr = message.getTextAttr();

            Text text = null;

            /* ????????? ????????? ?????? ???????????? ??????, ????????? ????????? ????????? ?????? ?????? */
            /* ??? ????????? ???????????? ???????????? ?????? ?????? ???????????? */
            /* ????????? ??????????????? ????????? ????????? ????????? ?????? ?????? */
            if(!textMode.equals(TextMode.CREATE)) {
                text = de.findTextById(textAttr.getId());
                if(text == null) return null; // ???????????? ???????????? MID ??? ?????? ???????????????, ???????????? TEXT ??? ?????? ???????????? ?????? ??? ?????? (???????????? ????????? ????????? ??? ????????? ????????? ???????)
                text.setTextAttribute(textAttr); // MQTT ??? ???????????? ????????? ?????? ???????????????
            }

            switch (textMode) {
                case CREATE:
                case MODIFY_START:
                case START_COLOR_CHANGE:
                case FINISH_COLOR_CHANGE:
                    publishProgress(message);
                    return null;
                case DRAG_LOCATION:
                case DRAG_EXITED:
                    text.setTextViewLocation();
                    return null;
                case DROP:
                    //de.addHistory(new DrawingItem(TextMode.DROP, textAttr));
                    text.setTextViewLocation();
                    publishProgress(message);
                    return null;
                case DONE:
                    publishProgress(message);
                    return null;
                case DRAG_ENDED:
                    return null;
                case ERASE:
                    //de.addHistory(new DrawingItem(TextMode.ERASE, textAttr));
                    publishProgress(message);
                    return null;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(MqttMessageFormat... messages) { // changeTextOnMainThread()
            MqttMessageFormat message = messages[0];

            TextMode textMode = message.getTextMode();
            TextAttribute textAttr = message.getTextAttr();

            Text text = de.findTextById(textAttr.getId());
            switch(textMode) {
                case CREATE:
                    Text newText = new Text(client.getDrawingFragment(), textAttr);
                    newText.getTextAttribute().setTextInited(true); // ???????????? ?????? ?????? ????????? ????????????
                    de.addTexts(newText);
                    //de.addHistory(new DrawingItem(TextMode.CREATE, textAttr));
                    newText.setTextViewProperties();
                    newText.addTextViewToFrameLayout();
                    newText.createGestureDetector();
                    //de.clearUndoArray();
                    break;
                case DRAG_STARTED:
                case DRAG_LOCATION:
                case DRAG_ENDED:
                    break;
                case DROP:
                    //de.clearUndoArray();
                    break;
                case DONE:
                    text.getTextView().setBackground(null); // ????????? ?????? ??????
                    text.setTextViewAttribute();
                    break;
                case ERASE:
                    text.removeTextViewToFrameLayout();
                    de.removeTexts(text);
                    //de.clearUndoArray();
                    //Log.e("texts size", Integer.toString(de.getTexts().size()));
                    break;
                case MODIFY_START:
                    text.getTextView().setBackground(de.getTextFocusBorderDrawable()); // ???????????? ??? ????????? ????????? ???????????? ???????????? ????????? ??????
                    //text.modifyTextViewContent(textAttr.getText());
                    break;
                case START_COLOR_CHANGE:
                    text.getTextView().setBackground(de.getTextHighlightBorderDrawble()); // ????????? ?????? ?????? ?????? ????????? ??????
                    break;
                case FINISH_COLOR_CHANGE:
                    text.getTextView().setBackground(null); // ????????? ?????? ?????? ?????? ??? ????????? ??????
                    text.setTextViewAttribute(); // ????????? ?????? ??????
                    break;
            }

            if(de.getHistory().size() == 1) {
                client.getBinding().undoBtn.setEnabled(true);
            }
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }

    static class MidTask extends AsyncTask<Void, Void, Void> {
        private MQTTClient client = MQTTClient.getInstance();
        private DrawingEditor de = DrawingEditor.getInstance();

        @Override
        protected Void doInBackground(Void... values) {
            if (de.getBackgroundImage() != null) {
                //de.setBackgroundImage(de.byteArrayToBitmap());
                publishProgress();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
            MyLog.i("mqtt", "mid onProgressUpdate()");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            MyLog.i("mqtt", "mid onPostExecute()");
            if (de.getHistory().size() > 0)
                client.getBinding().undoBtn.setEnabled(true);
            if (de.getUndoArray().size() > 0)
                client.getBinding().redoBtn.setEnabled(true);

            de.drawAllDrawingComponentsForMid();
            de.addAllTextViewToFrameLayoutForMid();
            client.getDrawingView().invalidate();

            for (int i = 0; i < de.getAutoDrawList().size(); i++) {
                ImageView imageView = new ImageView(MainActivity.context);
                imageView.setLayoutParams(new LinearLayout.LayoutParams(300, 300));
                AutoDraw autoDraw = de.getAutoDrawList().get(i);

                int x = (int) (autoDraw.getPoint().x * client.getDrawingView().getCanvasWidth() / autoDraw.getWidth());
                int y = (int) (autoDraw.getPoint().y * client.getDrawingView().getCanvasHeight() / autoDraw.getHeight());

                imageView.setX(x);
                imageView.setY(y);
                client.getBinding().drawingViewContainer.addView(imageView);
                GlideToVectorYou.init().with(MainActivity.context).load(Uri.parse(autoDraw.getUrl()), imageView);
                de.addAutoDrawImageView(imageView);
            }

            client.getProgressDialog().dismiss();
            MyLog.i("mqtt", "mid progressDialog dismiss");
        }
    }

}

