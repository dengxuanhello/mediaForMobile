package org.m4m.effects;

import android.content.Context;

import org.m4m.AudioFormat;
import org.m4m.Uri;
import org.m4m.android.AudioFormatAndroid;
import org.m4m.domain.MediaFormat;

import java.nio.ByteBuffer;

/**
 * 音频的混音:
 * 音频混音的原理: 量化的语音信号的叠加等价于空气中声波的叠加。
 * 反应到音频数据上，也就是把同一个声道的数值进行简单的相加，但是
 * 这样同时会产生一个问题，那就是相加的结果可能会溢出，当然为了解决
 * 这个问题已经有很多方案了，在这里我们采用简单的平均算法
 * (average audio mixing algorithm, 简称V算法)。
 * 我们假设音频文件是的采样率，通道和采样精度都是一样的,这样会便于处理。
 * 另外要注意的是，在源音频数据中是按照little-endian的顺序来排放的，PCM值为0表示没声音(振幅为0)。
 * Created by dengxuan on 17-8-11.
 */

public class MixAudioEffect  extends AudioEffect {
    private AudioReader reader1 = new AudioReader();
    private AudioReader reader2 = new AudioReader();
    private ByteBuffer byteBuffer1 = ByteBuffer.allocate(1024 * 1024);
    private ByteBuffer byteBuffer2 = ByteBuffer.allocate(1024 * 1024);
    private Uri uri1;
    private Uri uri2;
    private AudioFormatAndroid audioFormat;

    @Override
    public void applyEffect(ByteBuffer input, long timeProgress) {
        if(reader1.read(byteBuffer1) && reader2.read(byteBuffer2)){
            audioFormat = new AudioFormatAndroid("audio/mp4a-latm", 48000, 2);
            ByteBuffer byteBuffer = mixAudio(byteBuffer1,byteBuffer2);
            if (input.capacity() < byteBuffer.limit()){
                input = ByteBuffer.allocateDirect(byteBuffer.limit());
            }
            byteBuffer.position(0);
            input.position(0);
            input.limit(byteBuffer.limit());
            input.put(byteBuffer);
        }
    }

    public void setFileUri1(Context context, Uri uri, AudioFormat mediaFormat) {
        this.uri1 = uri;

        reader1.setFileUri(uri);
        reader1.start(context, mediaFormat);
    }

    public Uri getFileUri1() {
        return uri1;
    }

    public void setFileUri2(Context context, Uri uri, AudioFormat mediaFormat) {
        this.uri2 = uri;

        reader2.setFileUri(uri);
        reader2.start(context, mediaFormat);
    }

    public Uri getFileUri2() {
        return uri2;
    }

    public MediaFormat getMediaFormat() {
        return audioFormat;
    }

    private ByteBuffer mixAudio(ByteBuffer src1,ByteBuffer src2){
        ByteBuffer des = ByteBuffer.allocateDirect(src1.limit()>src2.limit()?src1.limit():src2.limit() + 2);
        src1.position(0);
        src2.position(0);
        byte[] src1B = src1.array();
        byte[] src2B = src2.array();
        short[] src1S = new short[src1.limit()/2];
        short[] src2S = new short[src2.limit()/2];
        int desL = src1.limit() > src1.limit()?src1.limit()/2:src1.limit()/2;
        short[] desS = new short[desL];
        for(int i=0 ; i < src1S.length ; i++){
            src1S[i] = (short) ((src1B[i*2]&0xff) | ((src1B[i*2+1]&0xff)<<8));
        }
        for(int i=0 ; i < src2S.length ; i++){
            src2S[i] = (short) ((src2B[i*2]&0xff) | ((src2B[i*2+1]&0xff)<<8));
        }
        int minLen = src1S.length < src2S.length?src1S.length:src2S.length;
        for(int i=0;i<minLen;i++){
            desS[i] = (short) ((src1S[i] + src2S[i])/2);
        }
        if(minLen == src1S.length){
            for(int j = minLen;j<src2S.length;j++){
                desS[j] = src2S[j];
            }
        }else {
            for(int j = minLen;j<src1S.length;j++){
                desS[j] = src1S[j];
            }
        }
        for(int i = 0;i<desS.length;i++){
            des.array()[2*i] = (byte) (desS[i] & 0x00FF);
            des.array()[2*i+1] = (byte) ((desS[i] & 0xFF00)>>8);
        }
        return des;
    }
}
