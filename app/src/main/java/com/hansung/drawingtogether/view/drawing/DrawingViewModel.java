package com.hansung.drawingtogether.view.drawing;

import android.app.AlertDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import android.util.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import com.hansung.drawingtogether.AutoDrawInterface;
import com.hansung.drawingtogether.R;
import com.hansung.drawingtogether.data.remote.model.Logger;
import com.hansung.drawingtogether.data.remote.model.MQTTClient;
import com.hansung.drawingtogether.data.remote.model.MyLog;
import com.hansung.drawingtogether.databinding.DialogAutoDrawBinding;
import com.hansung.drawingtogether.view.BaseViewModel;
import com.hansung.drawingtogether.view.SingleLiveEvent;
import com.hansung.drawingtogether.view.main.MQTTSettingData;
import com.hansung.drawingtogether.view.main.MainActivity;
import com.kakao.kakaolink.v2.KakaoLinkResponse;
import com.kakao.kakaolink.v2.KakaoLinkService;
import com.kakao.message.template.ButtonObject;
import com.kakao.message.template.ContentObject;
import com.kakao.message.template.FeedTemplate;
import com.kakao.message.template.LinkObject;
import com.kakao.message.template.TextTemplate;
import com.kakao.network.ErrorResult;
import com.kakao.network.callback.ResponseCallback;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class DrawingViewModel extends BaseViewModel {

    public final SingleLiveEvent<DrawingCommand> drawingCommands = new SingleLiveEvent<>();

    private DrawingEditor de = DrawingEditor.getInstance();
    private Logger logger = Logger.getInstance();

    /* MQTT ?????? ?????? */
    private MQTTClient client = MQTTClient.getInstance();
    private MQTTSettingData data = MQTTSettingData.getInstance();
    private String ip;
    private String port;
    private String topic;
    private String password;
    private boolean master;
    private String masterName;
    private String name;

    /* ?????? ???????????? ???????????? ?????? ?????? */
    private MutableLiveData<String> userNum = new MutableLiveData<>();
    private MutableLiveData<String> userPrint = new MutableLiveData<>();

    /* ????????? ?????? ?????? */
    private final int PICK_FROM_GALLERY = 0;
    private final int PICK_FROM_CAMERA = 1;
    private String photoPath;

    /* ????????? ?????? ?????? */
    private boolean micFlag = false;
    private boolean speakerFlag = false;
    private int speakerMode = 0; // 0: mute, 1: speaker on, 2: speaker loud
    private RecordThread recThread;
    private AudioManager audioManager = (AudioManager) MainActivity.context.getSystemService(Service.AUDIO_SERVICE);

    private ImageButton preMenuButton;

    /* ??????????????? ?????? ?????? */
    private MutableLiveData<String> autoDrawImage = new MutableLiveData<>();
    private String autoDrawImageUrl = "";

    public DrawingViewModel() {
        setUserNum(0);
        setUserPrint("");

        ip = data.getIp();
        port = data.getPort();
        topic = data.getTopic();
        name = data.getName();
        password = data.getPassword();
        master = data.isMaster();
        masterName = data.getMasterName();


        MyLog.i("MQTTSettingData", "ip : "
                + ip + ", port : " + port + ", topic : " + topic + ", password : " + password + ", name : " + name + ", isMaster : " + master + ", master : " + masterName);

        client.init(topic, name, master, this, ip, port, masterName);

        client.setAliveLimitCount(5);
        client.setCallback();
        client.subscribeAllTopics();


        // fixme nayeon for performance

//        for(int i=0; i<100; i++) {
//            try {
//                client.monitoringClientSetting(new MqttClient("tcp://" + ip + ":" + port, MqttClient.generateClientId(), new MemoryPersistence()), topic);
//            } catch (MqttException e) {
//                e.printStackTrace();
//            }
//        }


        de.setCurrentType(ComponentType.STROKE);    //fixme minj
        de.setCurrentMode(Mode.DRAW);

        /* Record Thread??? DrawingViewModel ?????? ??? ????????? ?????? */
        recThread = new RecordThread();
        recThread.setBufferUnitSize(4);
        recThread.start();
    }

    public void clickUndo(View view) {
        MyLog.d("button", "undo button click");

        de.getDrawingFragment().getBinding().drawingView.undo();
    }

    public void clickRedo(View view) {
        MyLog.d("button", "redo button click");

        de.getDrawingFragment().getBinding().drawingView.redo();
    }

    public void clickSave() {

        DrawingFragment fragment = de.getDrawingFragment();

        DrawingViewController dvc = fragment.getBinding().drawingViewContainer;
        dvc.setDrawingCacheEnabled(true);
        dvc.buildDrawingCache();
        Bitmap captureContainer = dvc.getDrawingCache();

        FileOutputStream fos;
        String fileName = "image-" + client.getTopic() + client.getSavedFileCount() + ".png";
        String filePath = Environment.getExternalStorageDirectory() + File.separator  + "Pictures"
                + File.separator + fileName;

        File fileCacheItem = new File(filePath);

        try {
            fos = new FileOutputStream(fileCacheItem);
            captureContainer.compress(Bitmap.CompressFormat.JPEG, 100, fos); // quality
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            fragment.getContext().sendBroadcast(new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(fileCacheItem))); // ????????? ????????? ??????
            MyLog.i("export", "capture path == " + filePath);
        }

        dvc.setDrawingCacheEnabled(false);

        Toast.makeText(fragment.getContext(), R.string.success_save, Toast.LENGTH_SHORT).show();
    }

    public void clickPen(View view) { // drawBtn1, drawBtn2, drawBtn3
        MyLog.d("button", "pen button click");
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.VISIBLE);
        changeClickedButtonBackground(view);

        if(de.getCurrentMode() == Mode.DRAW && de.getCurrentType() == ComponentType.STROKE) {
            drawingCommands.postValue(new DrawingCommand.PenMode(view));
        }

        de.setCurrentMode(Mode.DRAW);
        de.setCurrentType(ComponentType.STROKE);

        MyLog.i("drawing", "mode = " + de.getCurrentMode().toString() + ", type = " + de.getCurrentType().toString());
        //drawingCommands.postValue(new DrawingCommand.PenMode(view)); // color picker [ View Model ??? Navigator ??????, ????????? ?????? ?????? ]
        preMenuButton = (ImageButton)view; // ????????? ?????? ??? ?????? ????????? ??????????????? ???????????? ?????? (????????? ?????? ?????? ???????????? ????????? ?????????)
    }

    public void clickPencil(View view) {
        de.getDrawingFragment().getBinding().pencilBtn.setImageResource(R.drawable.pencil_1);
        de.getDrawingFragment().getBinding().highlightBtn.setImageResource(R.drawable.highlight_0);
        de.getDrawingFragment().getBinding().neonBtn.setImageResource(R.drawable.neon_0);

        de.setPenMode(PenMode.NORMAL);
    }

    public void clickHighlight(View view) {
        de.getDrawingFragment().getBinding().pencilBtn.setImageResource(R.drawable.pencil_0);
        de.getDrawingFragment().getBinding().highlightBtn.setImageResource(R.drawable.highlight_1);
        de.getDrawingFragment().getBinding().neonBtn.setImageResource(R.drawable.neon_0);

        de.setPenMode(PenMode.HIGHLIGHT);
    }

    public void clickNeon(View view) {
        de.getDrawingFragment().getBinding().pencilBtn.setImageResource(R.drawable.pencil_0);
        de.getDrawingFragment().getBinding().highlightBtn.setImageResource(R.drawable.highlight_0);
        de.getDrawingFragment().getBinding().neonBtn.setImageResource(R.drawable.neon_1);

        de.setPenMode(PenMode.NEON);
    }

    public void clickEraser(View view) {
        MyLog.d("button", "eraser button click");
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);
        changeClickedButtonBackground(view);

        if(de.getCurrentMode() == Mode.ERASE)
            drawingCommands.postValue(new DrawingCommand.EraserMode(view));
        de.setCurrentMode(Mode.ERASE);
        MyLog.i("drawing", "mode = " + de.getCurrentMode().toString());
    }

    public void clickText(View view) {
        MyLog.d("button", "text button click");
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);

        /* ???????????? ?????? ????????? ??????????????? ????????? ???????????? ?????? */
        /* ????????? ???????????? ?????? ???????????? ?????? ?????? ?????? ( ???????????? ????????? ?????? ?????? ) */
        /* ????????? ???????????? ???????????? ??? ?????? ????????? ????????? ??? ??? ????????? ???????????? */
        // de.setMidEntered(false);

        if(de.isMidEntered() /* && !de.getCurrentText().getTextAttribute().isTextInited() */) { // ????????? ????????? ??????
            showToastMsg("?????? ???????????? ?????? ??? ????????? ????????? ??????????????????");
            return;
        }
        //if(de.isTextBeingEdited()) return; // ?????? ????????? ?????? ?????? ??? ????????? ?????? ????????????
        /* ????????? ????????? ?????? ??? ?????? (Done Button ????????? ??? ??????) ?????? ????????? ???????????? & ??? ????????? ?????? ?????? (???????????? ??????) */
//        enableDrawingMenuButton(false);
//        changeClickedButtonBackground(view);

        de.setCurrentMode(Mode.TEXT);
        MyLog.i("drawing", "mode = " + de.getCurrentMode().toString());
        FrameLayout frameLayout = de.getDrawingFragment().getBinding().drawingViewContainer;


        ((MainActivity)de.getDrawingFragment().getActivity()).setVisibilityToolbarMenus(false);

        // ????????? ?????? ??????
        TextAttribute textAttribute = new TextAttribute(de.setTextStringId(), de.getMyUsername(),
                de.getTextSize(), de.getTextColor(), frameLayout.getWidth(), frameLayout.getHeight());

        Text text = new Text(de.getDrawingFragment(), textAttribute);
        text.createGestureDetector(); // Set Gesture ( Single Tap Up )

        text.changeTextViewToEditText(); // EditText ????????? ????????? ?????????, ????????? ?????? ?????? ??????
    }

    public void clickDone(View view) {
        MyLog.d("button", "done button click");

        // ?????? ???????????? ???????????? ?????? ???????????? ???????????? ?????????, ????????? ??? ???????????? ???????????? ?????? done ?????? ?????? ?????? (username == null ??? ???????????? ??????)
        // ???????????? ?????? ???????????? ????????? ?????? ?????? ?????????????????? ????????? ????????? ?????? ?????????, ?????? ????????? ????????? ????????? ??? ?????? ??????????????? ??????
        if(de.isMidEntered()
                && de.getCurrentText() != null && !de.getCurrentText().getTextAttribute().isTextInited()) { // ????????? ????????? ??????
                showToastMsg("?????? ???????????? ?????? ??? ?????????. ????????? ??????????????????.");
            return;
        }

        /* ????????? ????????? ????????? ?????? ????????? ????????? */
//        enableDrawingMenuButton(true);
//        changeClickedButtonBackground(preMenuButton); // ????????? ?????? ??? ?????? ????????? ????????? - ??? ?????? ?????? ??????

        Text text = de.getCurrentText();
        text.changeEditTextToTextView();

        changeClickedButtonBackground(preMenuButton); // ????????? ?????? ??? ?????? ????????? ????????? - ??? ?????? ?????? ??????

//        if(preMenuButton.equals(de.getDrawingFragment().getBinding().drawBtn)) // Draw Btn ??? ???????????? ??? ?????? ??????
        if(preMenuButton == de.getDrawingFragment().getBinding().drawBtn) // Draw Btn ??? ???????????? ??? ?????? ??????

            de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.VISIBLE); // ??? ?????? ????????????



        ((MainActivity)de.getDrawingFragment().getActivity()).setVisibilityToolbarMenus(true);
    }

    public void clickShape(View view) {
        MyLog.d("button", "shape button click");
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);

        changeClickedButtonBackground(view);
        de.setCurrentMode(Mode.DRAW);
        de.setCurrentType(ComponentType.RECT);

        preMenuButton = (ImageButton)view; // ????????? ?????? ??? ?????? ????????? ??????????????? ???????????? ?????? (????????? ?????? ?????? ???????????? ????????? ?????????)

        drawingCommands.postValue(new DrawingCommand.ShapeMode(view));
    }

    public void clickSelector(View view) {
        MyLog.d("button", "selector button click");
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);

        changeClickedButtonBackground(view);
        de.setCurrentMode(Mode.SELECT);
        MyLog.i("drawing", "mode = " + de.getCurrentMode().toString());
    }

    public void clickTextColor(View view) {
        MyLog.d("button", "text color button click");

        de.getCurrentText().finishTextColorChange();
    }

    public void clickWarp(View view) {
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);
        changeClickedButtonBackground(view);
        de.setCurrentMode(Mode.WARP);
    }

    public void clickAutoDraw(View view) {
        de.getDrawingFragment().getBinding().penModeLayout.setVisibility(View.INVISIBLE);
        changeClickedButtonBackground(view);
        de.setCurrentMode(Mode.AUTODRAW);

        DialogAutoDrawBinding binding = DialogAutoDrawBinding.inflate(LayoutInflater.from(MainActivity.context));
        binding.webview.getSettings().setJavaScriptEnabled(true);
        binding.webview.setWebChromeClient(new WebChromeClient());
        binding.webview.loadUrl("file:///android_asset/canvas.html");
        binding.webview.addJavascriptInterface(new AutoDrawInterface() {
            @JavascriptInterface
            @Override
            public void setImage(String imageUrl) {
                MyLog.i("img", imageUrl);
                autoDrawImageUrl = imageUrl;
            }
        }, "AutoDrawInterface");
        AlertDialog dialog = new AlertDialog.Builder(MainActivity.context)
                .setTitle("AutoDraw")
                .setCancelable(false)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!autoDrawImageUrl.isEmpty()) {
                            autoDrawImage.postValue(autoDrawImageUrl);
                        }
                        autoDrawImageUrl = "";
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (autoDrawImageUrl == null)
                            return;
                    }
                })
                .create();

        dialog.show();
    }

    public void changeClickedButtonBackground(View view) {
        LinearLayout drawingMenuLayout = de.getDrawingFragment().getBinding().drawingMenuLayout;

        /* preMenuButton -> ???????????? ????????? ?????? ???????????? ????????? ?????? ???????????? ??? NULL */
        /* ?????? ??? ?????? ?????? (?????? ???(?????????)) ??? ?????? */
        if(view == null) { view = drawingMenuLayout.getChildAt(0); }

        for(int i=0; i<drawingMenuLayout.getChildCount(); i++) {
            drawingMenuLayout.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
        }
        view.setBackgroundColor(Color.rgb(233, 233, 233));

        de.initSelectedBitmap();
    }

    /* ????????? ?????? ??? ???????????? ???????????? ?????? ?????? ??????, ????????? ???????????? */
//    public void enableDrawingMenuButton(Boolean bool) {
//        LinearLayout drawingMenuLayout = de.getDrawingFragment().getBinding().drawingMenuLayout;
//        drawingMenuLayout.setBackgroundColor(Color.TRANSPARENT);
//        drawingMenuLayout.setEnabled(bool);
//
////        for(int i=0; i<drawingMenuLayout.getChildCount(); i++) {
////            drawingMenuLayout.getChildAt(i).setEnabled(bool);
////            drawingMenuLayout.getChildAt(i).setBackgroundColor(Color.rgb(233, 233, 233));
////        }
//    }

    public boolean clickMic() {
        if (!micFlag) { // Record Start
            micFlag = true;
            synchronized (recThread.getAudioRecord()) {
                recThread.getAudioRecord().notify();
                MyLog.i("Audio", "Mic On - RecordThread Notify");
            }

            return true;
        } else { // Record Stop
            micFlag = false;
            recThread.setFlag(micFlag);
            MyLog.i("Audio", "Mic  Off");

            return false;
        }
    }

    public int clickSpeaker() {
        speakerMode = (speakerMode + 1) % 3; // 0, 1, 2, 0, 1, 2, ...

        if (speakerMode == 0) { // SPEAKER MUTE
            audioManager.setSpeakerphoneOn(false);
            speakerFlag = false;
            try {
                if (client.getClient().isConnected()) {
                    client.getClient().unsubscribe(client.getTopic_audio());
                }
            } catch (MqttException e) {
                MyLog.i("Audio", "Topic Audio Unsubscribe error : " + e.getMessage());
            }
            for (AudioPlayThread audioPlayThread : client.getAudioPlayThreadList()) {
                audioPlayThread.setFlag(speakerFlag);
                audioPlayThread.getBuffer().clear();
                MyLog.i("Audio", audioPlayThread.getUserName() + " buffer clear");
            }
        } else if (speakerMode == 1) { // SPEAKER ON
            speakerFlag = true;
            for (AudioPlayThread audioPlayThread : client.getAudioPlayThreadList()) {
                synchronized (audioPlayThread.getAudioTrack()) {
                    audioPlayThread.getAudioTrack().notify();
                }
            }
            client.subscribe(client.getTopic_audio());
        } else if (speakerMode == 2) { // SPEAKER LOUD
            audioManager.setSpeakerphoneOn(true);
        }

        return speakerMode;
    }

    public void getImageFromGallery(Fragment fragment) {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType(MediaStore.Images.Media.CONTENT_TYPE);
        fragment.startActivityForResult(galleryIntent, PICK_FROM_GALLERY);
    }

    public void getImageFromCamera(Fragment fragment) {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(fragment.getContext().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile(fragment);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (photoFile != null) {
                Uri uri = FileProvider.getUriForFile(fragment.getContext(), "com.hansung.drawingtogether.fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                fragment.startActivityForResult(cameraIntent, PICK_FROM_CAMERA);
            }
        }
    }

    /* ??????????????? ?????? ?????? ?????? ?????? ??? */
    public void clickInvite() {
        MyLog.i("KakaoLink", "Click KakaoLink Invite");

        /* ????????? ????????? ????????? ????????? */
//        Bitmap bitmap = BitmapFactory.decodeResource(MainActivity.context.getResources(), R.drawable.kakao_link_img);
//        File file = new File(MainActivity.context.getCacheDir(), "kakao_link_img.png");
//        try {
//            FileOutputStream stream = new FileOutputStream(file);
//            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//
//          KakaoLinkService.getInstance().uploadImage(MainActivity.context, false, file, new ResponseCallback<ImageUploadResponse>() {
//              @Override
//              public void onFailure(ErrorResult errorResult) {
//                  MyLog.e("KakaoLink", errorResult.getErrorMessage());
//              }
//
//              @Override
//              public void onSuccess(ImageUploadResponse result) {
//                  MyLog.i("KakaoLink", result.getOriginal().getUrl());  // ????????? ?????? url ??????
//              }
//          });

        /* ?????????????????? ?????????, ???????????? ?????? - ?????? ????????? */
        FeedTemplate feedTemplate = FeedTemplate.newBuilder(
                ContentObject.newBuilder(
                        "??????????????????????????????" + " - ????????? [" + topic + "]",
                        "http://k.kakaocdn.net/dn/bftFB6/bl2J1T0qwdk/RxMA99bZEhHkQIUxQDBkgk/kakaolink40_original.png",
                        LinkObject.newBuilder()
                                .setAndroidExecutionParams("topic=" + topic + "&password=" + password)
                                .setIosExecutionParams("topic=" + topic + "&password=" + password)
                                .build())
                        .setDescrption("#?????? #????????? #????????? #??????")
                        .build())
//                .setSocial(SocialObject.newBuilder()
//                        .setLikeCount(286)
//                        .setCommentCount(45)
//                        .setSharedCount(845)
//                .build())
                .addButton(new ButtonObject(
                        "????????? ??????",
                        LinkObject.newBuilder()
                                .setAndroidExecutionParams("topic=" + topic + "&password=" + password)
                                .setIosExecutionParams("topic=" + topic + "&password=" + password)
                                .build()))
                .build();


        /* ?????????????????? ?????????, ???????????? ?????? - ????????? ????????? */
//        TextTemplate textTemplate = TextTemplate.newBuilder("??????????????????",
//                LinkObject.newBuilder()
//                        .setAndroidExecutionParams("topic=" + topic + "&password=" + password)
//                        .setIosExecutionParams("topic=" + topic + "&password=" + password)
//                        .build())
//                .setButtonTitle("????????? ??????").build();

        /* ???????????? API??? ????????? ????????? */
        KakaoLinkService.getInstance().sendDefault(MainActivity.context, feedTemplate, new ResponseCallback<KakaoLinkResponse>() {
            @Override
            public void onFailure(ErrorResult errorResult) {
                MyLog.e("KakaoLink", "Failure: " + errorResult.getErrorMessage());
                showKakaogAlert("??????????????? ??????", errorResult.getErrorMessage());
            }

            @Override
            public void onSuccess(KakaoLinkResponse result) {
                MyLog.i("KakaoLink", "Success");
            }
        });
    }

    /* ??????????????? ?????? ??? */
    public void showKakaogAlert(String title, String message) {

        AlertDialog dialog = new AlertDialog.Builder(MainActivity.context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .create();

        dialog.show();

    }

    public File createImageFile(Fragment fragment) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(Environment.getExternalStorageDirectory() + "/Pictures", "seeseecallcall");
        if (!storageDir.exists()) storageDir.mkdirs();
        File  image = File.createTempFile(imageFileName, ".jpg", storageDir);
        photoPath = image.getAbsolutePath();
        MyLog.i("image", photoPath);

        return image;
    }

    private void showToastMsg(final String message) { Toast.makeText(de.getDrawingFragment().getActivity(), message, Toast.LENGTH_SHORT).show(); }

    public String getPhotoPath() {
        return photoPath;
    }

    public MutableLiveData<String> getUserNum() {
        return userNum;
    }

    public MutableLiveData<String> getUserPrint() { return userPrint; }

    public void setUserNum(int num) {
        userNum.postValue(num + "???");
    }

    public void setUserPrint(String user) { userPrint.postValue(user); }

    @Override
    public void onCleared() {
        super.onCleared();
        MyLog.i("lifeCycle", "DrawingViewModel onCleared()");
    }

}