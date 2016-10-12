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
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.V;
import static com.android.xperiatorchboost.R.layout.main;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "TAG_MAINACTIVITY";

    public Button bApply, bBackup;
    private TextView tvNetworkError;
    private String whichDownload;

    private Process mSU = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(main);

        onFinishInflate();

        if (new File(String.valueOf(String.valueOf(Environment.getExternalStorageDirectory().getPath()) + "/") + "flashled_calc_parameters.cfg").exists()) {
            bBackup.setText("Restore");
            Log.d(TAG, "Yedek var");
        } else {
            bApply.setEnabled(false);
            Log.d(TAG, "Yedek bulunamadı");
        }

        if (new File(String.valueOf(String.valueOf(Environment.getExternalStorageDirectory().getPath()) + "/") + "Download/flashled_calc_parameters.cfg").exists()) {
            bApply.setText("Apply");
            Log.d(TAG, "İndirilmiş");
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
            new BackgroundDownloadTask(MainActivity.this).execute();
            Log.d(TAG, "Arkaplan işlemi başladı");
        } else if (view == bBackup) {
            if (new File(String.valueOf(String.valueOf(Environment.getExternalStorageDirectory().getPath()) + "/") + "flashled_calc_parameters.cfg").exists()) {
                restoreTorch();
            } else {
                backupTorch();
                Log.d(TAG, "Yedek alındı");
            }
            bApply.setEnabled(true);
        }
    }

    public void onRadioButtonClicked(View view) {

        boolean checked = ((RadioButton) view).isChecked();

        String[] urls = {
                "https://drive.google.com/uc?export=download&id=0B6h8FtMQuShSWHdGSE9vWTBHUXM",
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

    public class BackgroundDownloadTask extends AsyncTask<Void, String, Void> {

        private ProgressDialog dialog;
        private Context context;
        private boolean mDownloaded = false;
        private boolean mCopied = false;

        BackgroundDownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {

            dialog = new ProgressDialog(context);

            dialog.setMessage("Lütfen bekleyin");
            dialog.setCancelable(true);

            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!new File(String.valueOf(Environment.getExternalStorageDirectory().getPath()) + "/" + "Download/flashled_calc_parameters.cfg").exists()) {
                publishProgress("İndiriliyor");


                DownloadManager.Request downloadRequest = new DownloadManager.Request(Uri.parse(whichDownload));

                downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "flashled_calc_parameters.cfg");
                downloadRequest.allowScanningByMediaScanner();
                downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                Log.d(TAG, "İndirme isteği alındı");

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                dm.enqueue(downloadRequest);

                try {
                    ourSleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "İndiriliyor");

                mDownloaded = true;
            } else {
                publishProgress("Kopyalanıyor");
                Log.d(TAG, "Dosya mevcut");
                try {
                    ourSleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                copyAndSetPermissions();
                mCopied = true;
                Log.d(TAG, "Kopyalandı ve izinler ayarlandı");
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            dialog.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            dialog.cancel();
            if (mDownloaded) {
                bApply.setText("Apply");
                Toast.makeText(context, "Please wait until download finish\nThen click apply", Toast.LENGTH_LONG).show();
            } else {
                bApply.setText("Download");
                if (mCopied) {
                    new AlertDialog.Builder(context)
                            .setTitle("Uygulandı")
                            .setCancelable(false)
                            .setMessage("Yeniden başlatmak istiyor musunuz?")
                            .setPositiveButton("Evet", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    rebootPhone();
                                }
                            }).setNegativeButton("Hayır", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                            .show();
                }
            }
        }
    }

    private void copyAndSetPermissions() {

        String sourcePath = "/mnt/sdcard/Download/flashled_calc_parameters.cfg";
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
    }

    private void backupTorch() {

        String destinationPath = "/mnt/sdcard/flashled_calc_parameters.cfg";
        String sourcePath = "/system/etc/flashled_calc_parameters.cfg";

        try {
            // root izni burada isteniyor..
            mSU = Runtime.getRuntime().exec("su");
            Log.d(TAG, "Superuser yetkisi alındı");
            DataOutputStream os = new DataOutputStream(this.mSU.getOutputStream());
            os.writeBytes("mount -o remount,rw /system /system" + "\n");
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
        String sourcePath = "/mnt/sdcard/flashled_calc_parameters.cfg";
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
                .setTitle("Uygulandı")
                .setCancelable(false)
                .setMessage("Yeniden başlatmak istiyor musunuz?")
                .setPositiveButton("Evet", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        rebootPhone();
                    }
                }).setNegativeButton("Hayır", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        })
                .show();
    }

    private void ourSleep(int sleepTime) throws InterruptedException {
        Thread.sleep(sleepTime);
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

    private void rebootPhone() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
            proc.waitFor();
        } catch (Exception ex) {
            Log.i(TAG, "Yeniden başlatılamıyor", ex);
        }
    }

}
