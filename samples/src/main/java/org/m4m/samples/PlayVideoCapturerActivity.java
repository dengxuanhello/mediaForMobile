/*
 * Copyright 2014-2016 Media for Mobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.m4m.samples;


import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.m4m.android.AndroidMediaObjectFactory;
import org.m4m.android.AudioFormatAndroid;
import org.m4m.android.VideoFormatAndroid;
import org.m4m.android.graphics.VideoEffect;
import org.m4m.domain.FileSegment;
import org.m4m.domain.IPreview;
import org.m4m.domain.Resolution;
import org.m4m.domain.graphics.TextureRenderer;
import org.m4m.effects.GrayScaleEffect;
import org.m4m.effects.InverseEffect;
import org.m4m.effects.MuteAudioEffect;
import org.m4m.effects.SepiaEffect;
import org.m4m.effects.TextOverlayEffect;
import org.m4m.effects.WaterMarkInfo;
import org.m4m.samples.controls.CameraCaptureSettingsPopup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class PlayVideoCapturerActivity extends ActivityWithTimeline implements CameraCaptureSettingsPopup.CameraCaptureSettings {

    private String videoRecordPath;
    private int previewWidth = 960;

    public org.m4m.IProgressListener progressListener = new org.m4m.IProgressListener() {
        @Override
        public void onMediaStart() {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isRecordingInProgress = true;
                        captureButton.setEnabled(true);
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaProgress(float progress) {
        }

        @Override
        public void onMediaDone() {
            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isRecordingInProgress = false;
                        showToast("Video saved to " + getVideoFilePath());
                        updateVideoFilePreview();
                        captureButton.setEnabled(true);
                    }
                });
            } catch (Exception e) {
            }
        }

        @Override
        public void onMediaPause() {
        }

        @Override
        public void onMediaStop() {
        }

        @Override
        public void onError(Exception exception) {
            try {
                final Exception e = exception;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String message = (e.getMessage() != null) ? e.getMessage() : e.toString();
                        showMessageBox("Capturing failed" + "\n" + message, null);

                        isRecordingInProgress = false;
                        captureButton.setEnabled(true);
                    }
                });
            } catch (Exception e) {
            }
        }
    };
    private AndroidMediaObjectFactory factory;
    private boolean recordAudio = true;
    private Handler handler;

    private boolean autoFocusSupported = false;
    private boolean autoFlashSupported = false;
    private ImageButton videoFilePreview;
    private ImageButton captureButton;
    private TextView fpsText;
    private GLSurfaceView glSurfaceView;
    private CheckBox muteCheckBox;

    class AllEffects implements org.m4m.IVideoEffect {
        private FileSegment segment = new FileSegment(0l, 0l);
        private ArrayList<org.m4m.IVideoEffect> videoEffects = new ArrayList<org.m4m.IVideoEffect>();
        private int activeEffectId;
        private long l;
        private long msPerFrame = 1;
        ArrayList<Long> lst = new ArrayList<Long>();
        static final int window = 10;

        public synchronized double getFps() {
            long sum = 0;

            for (Long aLong : lst) {
                sum += aLong;
            }
            return 1e9 * lst.size() / sum;
        }

        @Override
        public FileSegment getSegment() {
            return segment;
        }

        @Override
        public void setSegment(FileSegment segment) {
        }

        @Override
        public void start() {
            for (org.m4m.IVideoEffect effect : videoEffects) {
                effect.start();
            }
        }

        @Override
        public void applyEffect(int inTextureId, long timeProgress, float[] transformMatrix) {
            long currentTime = System.nanoTime();
            msPerFrame = currentTime - l;
            l = currentTime;
            synchronized (this) {
                lst.add(msPerFrame);
                if (lst.size() > window) {
                    lst.remove(0);
                }
            }
            handler.sendMessage(handler.obtainMessage());

            videoEffects.get(activeEffectId).applyEffect(inTextureId, timeProgress, transformMatrix);
        }

        @Override
        public void setInputResolution(Resolution resolution) {
            for (org.m4m.IVideoEffect videoEffect : videoEffects) {
                videoEffect.setInputResolution(resolution);
            }
        }

        @Override
        public void setFillMode(TextureRenderer.FillMode fillMode) {
            org.m4m.IVideoEffect activeEffect = videoEffects.get(activeEffectId);
            if (activeEffect != null)
                activeEffect.setFillMode(fillMode);
        }

        @Override
        public TextureRenderer.FillMode getFillMode() {
            org.m4m.IVideoEffect activeEffect = videoEffects.get(activeEffectId);
            return activeEffect != null ? activeEffect.getFillMode() : null;
        }

        @Override
        public void setAngle(int degrees) {
            for (org.m4m.IVideoEffect videoEffect : videoEffects) {
                videoEffect.setAngle(degrees);
            }
        }

        @Override
        public int getAngle() {
            return videoEffects.get(activeEffectId).getAngle();
        }

        public void setActiveEffectId(int activeEffectId) {
            this.activeEffectId = activeEffectId;
        }

        public int getActiveEffectId() {
            return activeEffectId;
        }

        public ArrayList<org.m4m.IVideoEffect> getVideoEffects() {
            return videoEffects;
        }
    }

    boolean isRecordingInProgress = false;


    SimpleExoPlayer simpleExoPlayer = null;
    private int camera_type = 0;
    org.m4m.VideoPlayCapture capture;
    Resolution encodedResolution = new Resolution(640, 480);
    private IPreview preview;
    AllEffects allEffects = new AllEffects();
    private MuteAudioEffect muteAudioEffect = new MuteAudioEffect();

    private int activeEffectId = 0;
    ScheduledExecutorService service;
    ScheduledFuture<?> scheduledFuture;

    private TextureRenderer.FillMode fillMode = TextureRenderer.FillMode.PreserveAspectFit;

    private void setViewIDs() {

        captureButton = (ImageButton) findViewById(R.id.streaming);

        videoFilePreview = (ImageButton) findViewById(R.id.preview);
        videoFilePreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });

        captureButton = (ImageButton) findViewById(R.id.streaming);
        fpsText = (TextView) findViewById(R.id.fpsText);
    }

    public void onCreate(Bundle icicle) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        super.onCreate(icicle);
        service = Executors.newSingleThreadScheduledExecutor();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.videoplay_capturer_activity);
        setViewIDs();

        Intent intent = getIntent();
        camera_type = intent.getIntExtra("CAMERA_TYPE", 0);

        createCamera();
        factory = new AndroidMediaObjectFactory(getApplicationContext());
        configureEffects(factory);
        createCapturePipeline();
        createPreview();

        updateVideoFilePreview();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                fpsText.setText(String.valueOf(String.format("%.0f", allEffects.getFps())));
            }
        };

        muteCheckBox = (CheckBox) findViewById(R.id.muteCheckBox);
        muteCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                    @Override
                                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                        if (isChecked) {
                                                            muteAudioEffect.setMute(true);
                                                        } else {
                                                            muteAudioEffect.setMute(false);
                                                        }
                                                    }
                                                }
        );
    }

    private void configureEffects(final AndroidMediaObjectFactory factory) {

        ArrayList<org.m4m.IVideoEffect> videoEffects = allEffects.getVideoEffects();

        videoEffects.clear();

        videoEffects.add(new VideoEffect(0, factory.getEglUtil()) {
        });
        videoEffects.add(new GrayScaleEffect(0, factory.getEglUtil()));
        videoEffects.add(new SepiaEffect(0, factory.getEglUtil()));
        videoEffects.add(new InverseEffect(0, factory.getEglUtil()));
        WaterMarkInfo waterMarkInfo = new WaterMarkInfo();
        waterMarkInfo.bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.shuiyinh);
        videoEffects.add(new TextOverlayEffect(waterMarkInfo,0, factory.getEglUtil()));

        /*if (camera_type == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            checkWorkingEffects();
        }*/
    }


    // removeTextOverlay
    private void checkWorkingEffects() {
        int removeIndex = 4;

        ImageButton effectText = (ImageButton) findViewById(R.id.effect_text);
        if (effectText != null) {
            ((ViewManager) findViewById(R.id.effect_text).getParent()).removeView(findViewById(R.id.effect_text));
        }

        if (allEffects.getVideoEffects().size() >= (removeIndex + 1)) {
            allEffects.getVideoEffects().remove(removeIndex); // remove TextOverLay
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (simpleExoPlayer != null) {
            stopRecording();

            saveSettings();

            destroyPreview();
            destroyCapturePipeline();
            destroyExoplayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (simpleExoPlayer == null) {
            createCamera();
            factory = new AndroidMediaObjectFactory(getApplicationContext());
            createCapturePipeline();
            configureEffects(factory);
            createPreview();
        }
        restoreSettings();
    }

    private void saveSettings() {
        //Save camera effect settings
        activeEffectId = allEffects.getActiveEffectId();
    }

    private void restoreSettings() {
        //Restore saved effect settings
        allEffects.setActiveEffectId(activeEffectId);
        preview.setActiveEffect(allEffects);
    }


    private void createCapturePipeline() {
        capture = new org.m4m.VideoPlayCapture(factory, progressListener);
        capture.setFillMode(fillMode);
        if (allEffects != null) {
            capture.addVideoEffect(allEffects);
        }
    }

    private void destroyCapturePipeline() {
        capture = null;
    }

    private void createPreview() {
        glSurfaceView = (GLSurfaceView) findViewById(R.id.video_play_glsurfaceview);
        glSurfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR);
        preview = capture.createPreview(glSurfaceView, simpleExoPlayer);
        preview.setFillMode(fillMode);

        if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            capture.setOrientation(90);
        } else if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            capture.setOrientation(0);
        }
        preview.start();
    }

    private void destroyPreview() {
        preview.stop();
        preview = null;
        ((RelativeLayout) findViewById(R.id.camera_layout)).removeView(glSurfaceView);
        glSurfaceView = null;
    }

    private void createCamera() {
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        // Measures bandwidth during playback. Can be null if not required.
        DefaultBandwidthMeter defaultBandwidthMeter = new DefaultBandwidthMeter();
        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "yourApplicationName"), defaultBandwidthMeter);
        // Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        // This is the MediaSource representing the media to be played.
        String fileUrl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath() + "/capture_1501812406568.mp4";
        MediaSource videoSource = new ExtractorMediaSource(Uri.parse("http://r8---sn-oguesn7y.c.youtube.com/videoplayback?id=604ed5ce52eda7ee&itag=22&source=youtube&sparams=expire,id,ip,ipbits,mm,mn,ms,mv,nh,pl,source&ip=202.32.147.10&ipbits=0&expire=19000000000&signature=2742A45C170667515836A9FDD75659086F71D917.3D65834800082AC81319F720B62A91E56BD8B4C3&key=cms1&cms_redirect=yes&mm=31&mn=sn-oguesn7y&ms=au&mt=1456997821&mv=m&nh=IgpwcjAyLm5ydDEwKgkxMjcuMC4wLjE&pl=23"), dataSourceFactory, extractorsFactory, null, null);
        //MediaSource videoSource = new ExtractorMediaSource(Uri.parse(fileUrl), dataSourceFactory, extractorsFactory, null, null);
        // SimpleExoPlayer
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        // Prepare the player with the source.
        simpleExoPlayer.prepare(videoSource);
        simpleExoPlayer.setPlayWhenReady(true);
    }

    private void destroyExoplayer() {
        if(simpleExoPlayer!=null){
            simpleExoPlayer.release();
            simpleExoPlayer = null;
        }
    }

    private void configureMediaStreamFormat() {

        org.m4m.VideoFormat videoFormat = new VideoFormatAndroid("video/avc", simpleExoPlayer.getVideoFormat().width, simpleExoPlayer.getVideoFormat().height);
        videoFormat.setVideoBitRateInKBytes(3000);
        videoFormat.setVideoFrameRate(25);
        videoFormat.setVideoIFrameInterval(1);
        capture.setTargetVideoFormat(videoFormat);

        if (recordAudio) {
            org.m4m.AudioFormat audioFormat = new AudioFormatAndroid("audio/mp4a-latm", 44100, 1);
            capture.setTargetAudioFormat(audioFormat);

            if (muteAudioEffect != null) {
                capture.addAudioEffect(muteAudioEffect);
            }
        }
    }

    public void toggleStreaming(View view) {
        updateUI();

        if (isRecordingInProgress) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    public void startRecording() {
        if (isRecordingInProgress) {
            Toast.makeText(this, "Can have only one active session.", Toast.LENGTH_SHORT).show();
        } else {
            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(true);
            }
            captureButton.setEnabled(false);
            capture();
        }
    }

    private void capture() {

        try {
            videoRecordPath = getVideoFilePath();
            capture.setTargetFile(videoRecordPath);
        } catch (IOException e) {
            String message = (e.getMessage() != null) ? e.getMessage() : e.toString();

            showMessageBox(message, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
        }
        configureMediaStreamFormat();

        capture.start();
    }

    public void stopRecording() {
        if (isRecordingInProgress) {
            captureButton.setEnabled(false);
            capture.stop();
            if(!TextUtils.isEmpty(videoRecordPath)){
                notifyMediaScanner(videoRecordPath);
                videoRecordPath = null;
            }
            configureEffects(factory);
            preview.setActiveEffect(allEffects);
        }
    }

    private File getAndroidMoviesFolder() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    }

    public String getVideoFilePath() {
        return getAndroidMoviesFolder().getAbsolutePath() + "/capture_"+System.currentTimeMillis()+".mp4";
    }

    public void onClickEffect(View view) {
        /*if (isRecordingInProgress) {
            return;
        }*/

        switch (view.getId()) {
            default: {
                String tag = (String) view.getTag();

                if (tag != null) {
                    allEffects.setActiveEffectId(Integer.parseInt(tag));
                    preview.setActiveEffect(allEffects);
                }
            }
            break;
        }
    }

    public void updateVideoFilePreview() {
        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(getVideoFilePath(), MediaStore.Video.Thumbnails.MINI_KIND);

        if (thumb == null) {
            videoFilePreview.setVisibility(View.INVISIBLE);
        } else {

            videoFilePreview.setImageBitmap(thumb);
        }
    }

    protected void playVideo() {
        String videoFilePath = getVideoFilePath();
        String videoUrl = "file:///" + videoFilePath;

        if (new File(videoFilePath).exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);

            android.net.Uri data = android.net.Uri.parse(videoUrl);
            intent.setDataAndType(data, "video/mp4");
            startActivity(intent);
        } else {
            ImageButton preview = (ImageButton) findViewById(R.id.preview);
            preview.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void displayResolutionChanged(int width, int height) {
        /*preview.stop();

        Camera.Parameters params = camera.getParameters();

        params.setPreviewSize(width, height);
        camera.setParameters(params);

        preview.updateCameraParameters();

        preview.start();*/
    }

    @Override
    public void videoResolutionChanged(int width, int height) {
        encodedResolution = new Resolution(width, height);
    }

    @Override
    public void audioRecordChanged(boolean bState) {
        recordAudio = bState;
    }

    private void updateUI() {
        ImageButton settingsButton = (ImageButton) findViewById(R.id.settings);
        ImageButton previewButton = (ImageButton) findViewById(R.id.preview);
        ImageButton changeCameraButton = (ImageButton) findViewById(R.id.change_camera);
        ScrollView container = (ScrollView) findViewById(R.id.effectsContainer);

        if (isRecordingInProgress) {
            captureButton.setImageResource(R.drawable.rec_inact);

            //container.setVisibility(View.VISIBLE);
            settingsButton.setVisibility(View.VISIBLE);
            previewButton.setVisibility(View.VISIBLE);
            changeCameraButton.setVisibility(View.VISIBLE);
        } else {
            captureButton.setImageResource(R.drawable.rec_act);

            //container.setVisibility(View.INVISIBLE);
            settingsButton.setVisibility(View.INVISIBLE);
            previewButton.setVisibility(View.INVISIBLE);
            changeCameraButton.setVisibility(View.INVISIBLE);
        }
    }

    protected void notifyMediaScanner(String dstMediaPath){
        if(!TextUtils.isEmpty(dstMediaPath)) {
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(android.net.Uri.fromFile(new File(dstMediaPath)));
            sendBroadcast(scanIntent);
        }
    }

    private void requestForChooseVideo(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(intent, IMPORT_FROM_GALLERY_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {

            case IMPORT_FROM_GALLERY_REQUEST: {
                if (resultCode == RESULT_OK) {
                    Uri selectedVideo = intent.getData();
                    if (selectedVideo == null) {
                        showToast("Invalid URI.");
                        return;
                    }

                }
            }
        }
    }
}
