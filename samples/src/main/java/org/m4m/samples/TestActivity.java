package org.m4m.samples;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.view.SurfaceView;

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

/**
 * Created by dengxuan on 17-8-3.
 */

public class TestActivity extends Activity {

    private SurfaceView surfaceView;
    private SimpleExoPlayer simpleExoPlayer;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_layout);
        surfaceView = (SurfaceView) findViewById(R.id.test_surfaceview);
        createCamera();
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
        String fileUrl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath() + "/capture_1501730720475.mp4";
        //MediaSource videoSource = new ExtractorMediaSource(Uri.parse("http://r8---sn-oguesn7y.c.youtube.com/videoplayback?id=604ed5ce52eda7ee&itag=22&source=youtube&sparams=expire,id,ip,ipbits,mm,mn,ms,mv,nh,pl,source&ip=202.32.147.10&ipbits=0&expire=19000000000&signature=2742A45C170667515836A9FDD75659086F71D917.3D65834800082AC81319F720B62A91E56BD8B4C3&key=cms1&cms_redirect=yes&mm=31&mn=sn-oguesn7y&ms=au&mt=1456997821&mv=m&nh=IgpwcjAyLm5ydDEwKgkxMjcuMC4wLjE&pl=23"), dataSourceFactory, extractorsFactory, null, null);
        MediaSource videoSource = new ExtractorMediaSource(Uri.parse(fileUrl), dataSourceFactory, extractorsFactory, null, null);
        // SimpleExoPlayer
        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        // Prepare the player with the source.
        simpleExoPlayer.prepare(videoSource);
        simpleExoPlayer.setPlayWhenReady(true);
        simpleExoPlayer.setVideoSurfaceView(surfaceView);
    }
}
