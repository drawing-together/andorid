package com.hansung.drawingtogether.data.remote.model;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.hansung.drawingtogether.view.drawing.DrawingEditor;
import com.hansung.drawingtogether.view.main.MainActivity;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.Getter;

// Logger Class
@Getter
public enum Logger {
    INSTANCE;

    private DrawingEditor de = DrawingEditor.getInstance();
    private MQTTClient client = MQTTClient.getInstance();

    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference storageRef = storage.getReference();

    private ProgressDialog progressDialog;

    private String log = "";
    private String uncaughtException = "";

    private boolean isAbnormalTerminated = false;


    public static Logger getInstance() { return INSTANCE; }

    public void info(String tag, String msg) { log += "[ INFO " + tag  + " ] : " + getLogContent(msg) + "\n"; }

    public void warn(String tag, String msg) { log += "[ WARN " + tag + " ] : " + getLogContent(msg) + "\n"; }

    public void error(String tag, String msg) { log += "[ ERROR " + tag + " ] : " + getLogContent(msg) + "\n"; }

    public void verbose(String tag, String msg) { log += "[ VERBOSE " + tag + " ] : " + getLogContent(msg) + "\n"; }

    public void debug(String tag, String msg) { log += "[ DEBUG " + tag + " ] : " + getLogContent(msg) + "\n"; }

    public void loggingUncaughtException(Thread thread, StackTraceElement[] ste) {

        uncaughtException += "\n\n" + "[ Thread Name ]\n" + thread.getName() + "\n\n";

        uncaughtException += "[ Print Stack Trace ]\n\n";

        for(int i=0; i< ste.length; i++)
            uncaughtException += ste[i].toString() + "\n";

    }


    public File createLogFile() {

        File logFile = new File(Environment.getExternalStorageDirectory() + File.separator + de.getMyUsername() + ".log");
        MyLog.e("file", Environment.getExternalStorageDirectory() + File.separator + de.getMyUsername() + ".log");

        try {
           FileWriter fw = new FileWriter(logFile);
           fw.write(log + uncaughtException);

           fw.close();

           return logFile;

        } catch (IOException io) { io.printStackTrace(); }

        return null;
    }

    public void deleteLogFile(File file) {

        if(file.exists()) file.delete(); // ??????????????? ????????? ???, ?????? ???????????? ????????? ?????? ??????

    }

    // Upload Log File To Firebase Storage
    public void uploadLogFile(ExitType exitType) {
        try {
            client.getClient().unsubscribe(client.getTopic_data()); // data topic ?????? ??????
        } catch (MqttException me) { me.printStackTrace(); }


        String filename = client.getTopic() + File.separator + de.getMyUsername() + ".log";

        File logFile = createLogFile();
        Uri fileUri = Uri.fromFile(logFile); // File to Uri
        StorageReference logRef = storageRef.child("log/" + filename); // ???????????? ????????? ?????? ??????

        MyLog.e("storage", "file path = " + "log/" + filename);

        UploadTask uploadTask = logRef.putFile(fileUri); // ?????????????????? ??????????????? ?????? ?????????

        setProgressDialog(); // ??????????????? ??????
        // new UploadProgressDialogShowThread(progressDialog).start(); // ??????????????? ?????????
        // progressDialog.show();


        while(!uploadTask.isSuccessful()); // ????????????????????? ????????? ??? ????????? ?????????

        deleteLogFile(logFile); // ???????????? ????????? ?????? ?????? Uri ?????? ??? ?????? ???????????? ????????? ?????? ????????????

        //progressDialog.dismiss();


        // new UploadProgressDialogDismissThread(progressDialog).start(); // ??????????????? ?????????

        if(exitType == ExitType.ABNORMAL) {
            //new ErrorAlertDialogThread().start(); // ?????? ???????????? ???????????? ????????? ?????????
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private void setProgressDialog() {
        progressDialog = new ProgressDialog(MainActivity.context);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setTitle("?????? ??????");
        progressDialog.setMessage("?????? ?????? ????????? ???");
        progressDialog.setCancelable(false);
    }


    class ErrorAlertDialogThread extends Thread {

        private Logger logger = Logger.getInstance();

        @Override
        public void run() {
            Looper.prepare();

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.context);
            builder.setTitle("??????");
            builder.setMessage(logger.getUncaughtException());
            builder.setCancelable(false); // ?????? ?????? ?????? ??? ?????????????????? ???????????? ?????????

            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    MyLog.d("button", "error dialog ok button click");

                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            MyLog.i("uncaught exception", "error dialog show");

            Looper.loop();
        }
    }


    private String getLogContent(String msg) {
        return "( " + getTime(System.currentTimeMillis()) + " ) " + de.getMyUsername() + " : " + msg;
    }


    public String getTime(long time) {
        Date date = new Date(time);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        return formatter.format(date);
    }

}

