package org.m4m;

import org.m4m.domain.CapturePipeline;
import org.m4m.domain.IAndroidMediaObjectFactory;
import org.m4m.domain.IMicrophoneSource;
import org.m4m.domain.IPreview;
import org.m4m.domain.IVideoPlaySource;

import java.util.Collection;
import java.util.LinkedList;
/**
 * Created by dengxuan on 17-8-3.
 */

public class VideoPlayCapture extends CapturePipeline {
    private IMicrophoneSource microphoneSource;
    private IVideoPlaySource videoPlaySource;
    private IPreview previewRender;
    private Object simpleExoPlayer;

    /**
     * Instantiates an object with Android base-layer.
     *
     * @param factory          IAndroidMediaObjectFactory class object.
     * @param progressListener Progress listener.
     * @see IAndroidMediaObjectFactory
     * @see IProgressListener
     */
    public VideoPlayCapture(IAndroidMediaObjectFactory factory, IProgressListener progressListener) {
        super(factory, progressListener);
    }

    /**
     * Sets target audio format.
     *
     * @param mediaFormat target audio format specifying audio parameters, including sample rate and number of channels.
     */
    @Override
    public void setTargetAudioFormat(AudioFormat mediaFormat) {
        super.setTargetAudioFormat(mediaFormat);
        microphoneSource = androidMediaObjectFactory.createMicrophoneSource();
        microphoneSource.configure(mediaFormat.getAudioSampleRateInHz(), mediaFormat.getAudioChannelCount());
    }

    /**
     * Sets target video format.
     *
     * @param mediaFormat Target video format specifying video parameters.
     */
    @Override
    public void setTargetVideoFormat(VideoFormat mediaFormat) {
        super.setTargetVideoFormat(mediaFormat);
    }

    /**
     * Sets a camera to be used for capturing.
     *
     * @param simpleExoPlayer simpleExoPlayer object created externally
     */
    public void setCamera(Object simpleExoPlayer) {
        if (videoPlaySource == null) {
            videoPlaySource = androidMediaObjectFactory.createVideoPlaySource();
        }
        videoPlaySource.setSource(simpleExoPlayer);
        this.simpleExoPlayer = simpleExoPlayer;
    }

    /**
     * @param mGLView GLSurfaceView object created externally.
     * @param player  Camera object created externally.
     * @return IPreview
     */
    public IPreview createPreview(Object mGLView, Object player) {
        this.simpleExoPlayer = player;

        if (null == previewRender) {
            previewRender = androidMediaObjectFactory.createPreviewRender(mGLView, player);
        }

        if (videoEffector == null) {
            videoEffector = androidMediaObjectFactory.createVideoEffector();
        }
        if (previewRender != null && player != null) {
            videoEffector.enablePreview(previewRender);
        }

        return previewRender;
    }

    /**
     * Adds a user's video effect to a collection of video effects.
     *
     * @param effect Video effect to be added.
     * @see IVideoEffect
     */
    public void addVideoEffect(IVideoEffect effect) {
        if (videoEffector == null) {
            videoEffector = androidMediaObjectFactory.createVideoEffector();
        }
        videoEffector.getVideoEffects().add(effect);
    }

    /**
     * Adds a user's audio effect to a collection of audio effects.
     *
     * @param effect Audio effect to be added.
     * @see IAudioEffect
     */
    public void addAudioEffect(IAudioEffect effect) {
        if (audioEffector == null) {
            audioEffector = androidMediaObjectFactory.createAudioEffects();
        }
        audioEffector.getAudioEffects().add(effect);
    }

    /**
     * Removes a video effect from the collection of video effects.
     *
     * @param effect Video effect to be removed.
     * @see IVideoEffect
     */
    public void removeVideoEffect(IVideoEffect effect) {
        videoEffector.getVideoEffects().remove(effect);
    }

    /**
     * Returns effect collected set in pipeline
     *
     * @return Read-only collection of effects used.
     * @see IVideoEffect
     */
    public Collection<IVideoEffect> getVideoEffects() {
        return (Collection<IVideoEffect>) videoEffector.getVideoEffects().clone();
    }

    /**
     * Sets target connection to a streaming server.
     *
     * @param parameters Streaming server connection parameters.
     * @see StreamingParameters
     */
    @Override
    public void setTargetConnection(StreamingParameters parameters) {
        super.setTargetConnection(parameters);
    }

    @Override
    public void setOrientation(int degrees) {
        super.setOrientation(degrees);
        if (previewRender != null) {
            previewRender.setOrientation(degrees);
        }
    }

    /**
     * Configures capture pipeline.
     */
    @Override
    protected void setMediaSource() {

        setCamera(simpleExoPlayer);

        if (microphoneSource != null) {
            pipeline.setMediaSource(microphoneSource);
        }

        if (videoPlaySource != null) {
            pipeline.setMediaSource(videoPlaySource);
        }
    }

    /**
     * Stops capturing.
     */
    @Override
    public void stop() {
        super.stop();
        if (previewRender != null) {
            LinkedList<IVideoEffect> videoEffects = videoEffector.getVideoEffects();

            videoEffector = androidMediaObjectFactory.createVideoEffector();
            videoEffector.getVideoEffects().addAll(videoEffects);
            videoEffector.enablePreview(previewRender);
        }
        if (microphoneSource != null) {
            microphoneSource = null;
        }
    }
}
