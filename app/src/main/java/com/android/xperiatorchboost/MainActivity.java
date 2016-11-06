package com.android.xperiatorchboost;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.R.id.copy;
import static android.R.id.input;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "TAG_MAINACTIVITY";

    public Button bApply, bBackup;
    private TextView tvNetworkError;
    private String whichDownload;
    private String path = "/sdcard/Download/flashled_calc_parameters.cfg";
    private boolean nothingSelected = true;

    // declare the dialog as a member field of your activity
    private ProgressDialog mProgressDialog;

    private Process mSU = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        onFinishInflate();
        if (new File(String.valueOf(Environment.getExternalStorageDirectory().getPath() + "/") + "torch_backup/flashled_calc_parameters.cfg").exists()) {
            bBackup.setText(getResources().getString(R.string.restore_text));
            Log.d(TAG, "Yedek var");
        } else {
            bApply.setEnabled(false);
            Log.d(TAG, "Yedek bulunamadı");
        }

        boolean mNetworkAvailable = checkConnection();

        if (!mNetworkAvailable) {
            tvNetworkError.setVisibility(View.VISIBLE);
            bBackup.setEnabled(false);
            bApply.setEnabled(false);
            Log.d(TAG, "Ağ erişimi yok");
        }
    }

    @Override
    public void onClick(View view) {
        if (view == bApply) {
            if (nothingSelected)
                Toast.makeText(MainActivity.this, getResources().getString(R.string.toast_selectone_text), Toast.LENGTH_LONG).show();
            else
                startDownloadTask();
            Log.d(TAG, "Arkaplan işlemi başladı");
        } else if (view == bBackup) {
            if (new File(String.valueOf(String.valueOf(Environment.getExternalStorageDirectory().getPath()) + "/") + "torch_backup/flashled_calc_parameters.cfg").exists()) {
                restoreTorch();
            } else {
                backupTorch();
                bApply.setEnabled(true);
                bBackup.setText(getResources().getString(R.string.restore_text));
                Log.d(TAG, "Yedek alındı");
            }
        } else if(view.getId() == R.id.fabRestart) {
            new AlertDialog.Builder(MainActivity.this)
                    .setCancelable(false)
                    .setMessage(getResources().getString(R.string.dialog_reallyreboot_text))
                    .setPositiveButton(getResources().getString(R.string.dialog_yes_text), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            rebootPhoneLikeSystem();
                        }
                    }).setNegativeButton(getResources().getString(R.string.dialog_no_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
                    .show();

        }
    }

    public void onRadioButtonClicked(View view) {

        boolean checked = ((RadioButton) view).isChecked();
        nothingSelected = false;

        String[] urls = {"https://drive.google.com/uc?export=download&id=0B6h8FtMQuShSWHdGSE9vWTBHUXM",
                "https://drive.google.com/uc?export=download&id=0B6h8FtMQuShSWUp4dWt2cGZaUGc",
                "https://drive.google.com/uc?export=download&id=0B6h8FtMQuShSOUVtaEZJS1p0TW8",
                "https://drive.google.com/uc?export=download&id=0B6h8FtMQuShSQlV5d2lnMFN2bWc"};

        switch (view.getId()) {
            case R.id.rbJExtreme:
                if (checked)
                    whichDownload = urls[0];
                break;
            case R.id.rbExtreme:
                if (checked)
                    whichDownload = urls[1];
                break;
            case R.id.rbJMedium:
                if (checked)
                    whichDownload = urls[2];
                break;
            case R.id.rbMedium:
                if (checked)
                    whichDownload = urls[3];
                break;
        }
    }

    // bağlantı kontrolü
    private boolean checkConnection() {
        ConnectivityManager mCon = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = mCon.getActiveNetworkInfo();

        return info != null && info.isAvailable();
    }

    private void startDownloadTask() {
        // instantiate it within the onCreate method
        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage(getResources().getString(R.string.dialog_downloading_text));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);

        // execute this when the downloader must be fired
        final DownloadTask downloadTask = new DownloadTask(MainActivity.this);
        downloadTask.execute(whichDownload);

        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                downloadTask.cancel(true);
            }
        });
    }

    // usually, subclasses of AsyncTask are declared inside the activity class.
    // that way, you can easily modify the UI thread from here
    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;

        DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(String... sUrl) {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(sUrl[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return "Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage();
                    }

                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();
                    output = new FileOutputStream(path);

                    byte data[] = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        if (isCancelled()) {
                            input.close();
                            return null;
                        }
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) // only if total length is known
                            publishProgress((int) (total * 100 / fileLength));
                        output.write(data, 0, count);
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (output != null)
                            output.close();
                        if (input != null)
                            input.close();
                    } catch (IOException ignored) {
                    }

                    if (connection != null)
                        connection.disconnect();
                }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            mProgressDialog.dismiss();
            if (result != null)
                Toast.makeText(context,"Download error: "+result, Toast.LENGTH_LONG).show();
            else {
                copyAndSetPermissions(context);
            }
        }

    }

    private void copyAndSetPermissions(Context context) {

        String sourcePath = "/mnt" + path;
        String destinationPath = "/system/etc/flashled_calc_parameters.cfg";

        try {
            // root izni burada isteniyor..
            mSU = Runtime.getRuntime().exec("su");
            Log.d(TAG, "Superuser yetkisi alındı");
            moveFile(sourcePath, destinationPath);
            this.mSU.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) {
        }
        showCompeteDialog(context);
    }

    private void backupTorch() {

        String destinationPath = String.valueOf(Environment.getExternalStorageDirectory().getPath() + "/") + "torch_backup/flashled_calc_parameters.cfg";
        String sourcePath = "/system/etc/flashled_calc_parameters.cfg";
        String mDir = String.valueOf(Environment.getExternalStorageDirectory().getPath() + "/") + "torch_backup/";

        try {
            // root izni burada isteniyor..
            mSU = Runtime.getRuntime().exec("su");
            Log.d(TAG, "Superuser yetkisi alındı");
            DataOutputStream os = new DataOutputStream(this.mSU.getOutputStream());
            os.writeBytes("mount -o remount,rw /system /system" + "\n");

            if (!new File(String.valueOf(Environment.getExternalStorageDirectory().getPath() + "/") + "torch_backup/").exists())
                os.writeBytes("mkdir " + mDir + "\n");

            os.writeBytes("cp " + sourcePath + " " + destinationPath + "\n");
            os.writeBytes("exit\n");
            os.flush();
            this.mSU.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) {
        }

        Toast.makeText(MainActivity.this, destinationPath + " dizinine kaydedildi", Toast.LENGTH_LONG).show();
    }

    private void restoreTorch() {
        String sourcePath = String.valueOf(Environment.getExternalStorageDirectory().getPath() + "/") + "torch_backup/flashled_calc_parameters.cfg";;
        String destinationPath = "/system/etc/flashled_calc_parameters.cfg";

        try {
            // root izni burada isteniyor..
            mSU = Runtime.getRuntime().exec("su");
            copyFile(sourcePath, destinationPath);
            this.mSU.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) {
        }

        new AlertDialog.Builder(MainActivity.this)
                .setTitle(getResources().getString(R.string.dialog_applied_text))
                .setCancelable(false)
                .setMessage(getResources().getString(R.string.dialog_reboot_text))
                .setPositiveButton(getResources().getString(R.string.dialog_yes_text), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        rebootPhoneLikeSystem();
                    }
                }).setNegativeButton(getResources().getString(R.string.dialog_no_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        })
                .show();
    }

    private void onFinishInflate() {
        bApply = (Button) findViewById(R.id.bApply);
        bBackup = (Button) findViewById(R.id.bBackup);
        bApply.setOnClickListener(this);
        bBackup.setOnClickListener(this);
        tvNetworkError = (TextView) findViewById(R.id.tvInternetError);
    }

    private void moveFile(String src, String dest) throws IOException {
        DataOutputStream os = new DataOutputStream(this.mSU.getOutputStream());
        os.writeBytes("mount -o remount,rw /system /system" + "\n");
        os.writeBytes("mv " + src + " " + dest + "\n");
        os.writeBytes("chmod 644 " + dest + "\n");
        os.writeBytes("exit\n");
        os.flush();
    }

    private void copyFile(String src, String dest) throws IOException {
        DataOutputStream os = new DataOutputStream(this.mSU.getOutputStream());
        os.writeBytes("mount -o remount,rw /system /system" + "\n");
        os.writeBytes("cp " + src + " " + dest + "\n");
        os.writeBytes("chmod 644 " + dest + "\n");
        os.writeBytes("exit\n");
        os.flush();
    }

    // [YENI] power menuden dokunulmuş gibi..
    private void rebootPhoneLikeSystem() {
        MainActivity.this.finish();
        try {
            mSU = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(this.mSU.getOutputStream());
            os.writeBytes("svc power reboot" + "\n");
            os.writeBytes("exit\n");
            this.mSU.waitFor();
        } catch (Exception ex) {
            Log.i(TAG, "Yeniden başlatılamıyor", ex);
        }
    }

    private void showCompeteDialog(Context c) {
        new AlertDialog.Builder(c)
                .setTitle(getResources().getString(R.string.dilaog_title))
                .setMessage(getResources().getString(R.string.dilaog_message))
                .setPositiveButton(getResources().getString(R.string.dialog_yes_text), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        rebootPhoneLikeSystem();
                    }
                })
                .setNegativeButton(getResources().getString(R.string.dialog_no_text), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }
}