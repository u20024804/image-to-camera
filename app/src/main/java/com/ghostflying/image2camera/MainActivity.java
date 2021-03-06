package com.ghostflying.image2camera;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity implements BaseAlertDialogFragment.OnFragmentInteractionListener {
    private static final int PICK_IMAGE = 10;
    private static final String IMAGE_TYPE = "image/*";
    private static final String INGRESS_PACKAGE_NAME = "com.nianticproject.ingress";

    private Uri outputUri;
    private List<AppInfo> cameraApps;

    @Bind(R.id.app_choose)
    View mAppChoose;
    @Bind(R.id.default_app)
    TextView mDefaultApp;
    @Bind(R.id.checkbox_only_work_for_ingress)
    CheckBox mWorkForIngress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        loadDefaultApp();
        loadWorkForIngress();
        Intent cameraIntent = getIntent();
        if (cameraIntent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE)){
            if (INGRESS_PACKAGE_NAME.equals(getCallingPackage())){
                startPickImages(cameraIntent);
            }
            else {
                SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);

                if (preferences.getBoolean(SettingUtil.ONLY_WORK_FOR_INGRESS, SettingUtil.DEFAULT_ONLY_WORK_FOR_INGRESS)){
                    String packageName = preferences.getString(SettingUtil.DEFAULT_CAMERA_APP, null);
                    String activityName = preferences.getString(SettingUtil.DEFAULT_CAMERA_APP_ACTIVITY, null);
                    if (packageName == null || activityName == null){
                        Toast.makeText(this, R.string.toast_please_set_default_camera_app, Toast.LENGTH_SHORT).show();
                    }
                    else {
                        ComponentName componentName = new ComponentName(packageName, activityName);
                        cameraIntent.setComponent(componentName);
                        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                        startActivity(cameraIntent);
                        finish();
                    }
                }
                else {
                    startPickImages(cameraIntent);
                }
            }
        }
    }

    private void startPickImages(Intent cameraIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            ClipData clipData = cameraIntent.getClipData();
            outputUri = clipData.getItemAt(0).getUri();
        }

        // compatibility for system below lollipop
        if (outputUri == null){
            Bundle extra = cameraIntent.getExtras();
            outputUri = extra.getParcelable(MediaStore.EXTRA_OUTPUT);
        }
        pickImages();
    }

    @OnClick(R.id.app_choose)
    public void chooseApp(){
        new LoadAppTask().execute();
    }

    @OnCheckedChanged(R.id.checkbox_only_work_for_ingress)
    public void onlyForIngressChanged(boolean checked){
        SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(SettingUtil.ONLY_WORK_FOR_INGRESS, checked).apply();
    }

    private void pickImages() {
        Intent intent = new Intent();
        intent.setType(IMAGE_TYPE);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)), PICK_IMAGE);
    }

    private void saveFile(Uri source, Uri des){
        try{
            final InputStream inputStream = getContentResolver().openInputStream(source);
            final File outputFile = new File(des.getPath());
            final OutputStream outputStream = new FileOutputStream(outputFile);

            try{
                try{
                    final byte[] buffer = new byte[1024];
                    int read;

                    while ((read = inputStream.read(buffer)) != -1)
                        outputStream.write(buffer, 0, read);

                    outputStream.flush();

                    // copy images successfully.
                    setResult(Activity.RESULT_OK);
                }
                finally {
                    inputStream.close();
                    outputStream.close();
                }
            }
            catch (IOException e){
                handleException(e);
            }
        }
        catch (FileNotFoundException e){
            handleException(e);
        }
    }

    private void handleException(Exception e){
        e.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, R.string.io_exception_toast, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteFile(Uri file){
        File outputFile = new File(file.getPath());
        outputFile.delete();
    }

    private void handleNotSelect(){
        Toast.makeText(this, R.string.not_select_toast, Toast.LENGTH_SHORT).show();
        deleteFile(outputUri);
        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE) {
            if (data == null || resultCode == Activity.RESULT_CANCELED) {
                handleNotSelect();
                setResult(RESULT_CANCELED);
                return;
            }
            if (resultCode == Activity.RESULT_OK){
                new SaveFileTask().execute(data.getData(), outputUri);
                setResult(RESULT_OK);
            }
        }
    }

    @Override
    public void onPositiveButtonClick(int value, int title) {
        if (cameraApps != null){
            SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);
            if (value < cameraApps.size()){
                AppInfo app = cameraApps.get(value);
                preferences.edit()
                        .putString(SettingUtil.DEFAULT_CAMERA_APP_ACTIVITY, app.activityName)
                        .putString(SettingUtil.DEFAULT_CAMERA_APP_NAME, app.appName)
                        .putString(SettingUtil.DEFAULT_CAMERA_APP, app.packageName)
                        .apply();
                loadDefaultApp();
            }
        }
    }

    @Override
    public void onNegativeButtonClick(int value, int title) {

    }

    private void loadDefaultApp(){
        SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);
        mDefaultApp.setText(preferences.getString(SettingUtil.DEFAULT_CAMERA_APP_NAME, getString(R.string.click_to_choose)));
    }

    private void loadWorkForIngress(){
        SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);
        mWorkForIngress.setChecked(preferences.getBoolean(SettingUtil.ONLY_WORK_FOR_INGRESS, SettingUtil.DEFAULT_ONLY_WORK_FOR_INGRESS));
    }

    private class SaveFileTask extends AsyncTask<Uri, Void, Void>{
        DialogFragment dialogFragment;

        @Override
        protected void onPreExecute() {
            dialogFragment = ImageReadDialogFragment.newInstance();
            dialogFragment.show(getFragmentManager(), null);
        }

        @Override
        protected Void doInBackground(Uri... params) {
            saveFile(params[0], params[1]);
            return null;
        }

        @Override
        protected void onPostExecute(Void result){
            dialogFragment.dismiss();
            finish();
        }
    }

    private class LoadAppTask extends AsyncTask<Void, Void, Void>{
        private DialogFragment dialogFragment;
        private ArrayList<String> appNames;
        private int checked;

        @Override
        protected void onPreExecute() {
            dialogFragment = AppLoadingDialogFragment.newInstance();
            dialogFragment.show(getFragmentManager(), null);
        }

        @Override
        protected Void doInBackground(Void... params) {
            final Intent mainIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            final PackageManager packageManager = MainActivity.this.getPackageManager();
            final List<ResolveInfo> pkgAppsList = packageManager.queryIntentActivities(mainIntent, 0);

            SharedPreferences preferences = getSharedPreferences(SettingUtil.SETTING_NAME, MODE_PRIVATE);
            String defaultApp = preferences.getString(SettingUtil.DEFAULT_CAMERA_APP_ACTIVITY, null);

            cameraApps = new ArrayList<>(pkgAppsList.size());
            appNames = new ArrayList<>(pkgAppsList.size());
            for (ResolveInfo each : pkgAppsList){
                if (!each.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID)){
                    AppInfo info = new AppInfo();
                    info.appName = each.loadLabel(packageManager).toString();
                    info.activityName = each.activityInfo.name;
                    info.packageName = each.activityInfo.packageName;
                    cameraApps.add(info);
                    appNames.add(info.appName);

                    if (defaultApp != null && info.activityName.equals(defaultApp)){
                        checked = cameraApps.size() - 1;
                    }
                }
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            dialogFragment.dismiss();
            DialogFragment chooseDialog = SingleChooseDialogFragment
                    .newInstance(R.string.dialog_title_app_choose, appNames, checked);
            chooseDialog.show(getFragmentManager(), null);
        }
    }

    private static class AppInfo{
        String packageName;
        String activityName;
        String appName;
    }
}
