/*
 *     LM AudioSpy is an audio recording app for Android version 5.1
 *     Copyright (C) 2017-2018 Loremar Marabillas
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.lmaudiospy;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by Loremar on 15/02/2018.
 * Class contains functions used for processing audio input.
 */

final class AudioProcessingTools {
    private static final String LOG = "Loremar_Logs";
    static void writeWavHeader(FileChannel fChannel, int channelMask, int sampleRate, int encoding) throws IOException {
        short channels;
        switch (channelMask) {
            case AudioFormat.CHANNEL_IN_MONO:
                channels = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                channels = 2;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable channel mask");
        }

        short bitDepth;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                bitDepth = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                bitDepth = 16;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitDepth = 32;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable encoding");
        }

        writeWavHeader(fChannel, channels, sampleRate, bitDepth);
    }

    private static void writeWavHeader(FileChannel fChannel, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.nativeOrder())
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        fChannel.write(ByteBuffer.wrap(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        }).order(ByteOrder.LITTLE_ENDIAN));
    }

    static class MaxAmplitude{
        int value;
        MaxAmplitude(int value){
            this.value = value;
        }
    }

    /**
     * Reads audio data and applies gain. This method also takes consideration of the format of the audio data being recorded
     * @param buffer            buffer to store recorded audio
     * @param record            AudioRecord object used to read audio data
     * @param readBufferSize    size of buffer specified in creating AudioRecord object
     * @param gain              level of gain to apply on recorded audio
     */
    static void readAudioApplyGain(ByteBuffer buffer, AudioRecord record, int readBufferSize, int gain, MaxAmplitude maxAmplitude){
        int read;
        switch(record.getAudioFormat()) {
            case AudioFormat.ENCODING_PCM_8BIT:
                read = record.read(buffer, readBufferSize);
                for (int i = 0; i < read; i++) {
                    byte b = buffer.get(i);
                    if(b> maxAmplitude.value){
                        maxAmplitude.value = b;
                    }
                    b = (byte) Math.min(Math.max(b * gain, Byte.MIN_VALUE), Byte.MAX_VALUE);
                    buffer.put(i, b);
                }
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                ShortBuffer audioBufferShort = buffer.asShortBuffer();
                read = record.read(buffer, readBufferSize);
                for (int i = 0; i < (read / 2); i++) {
                    short s = audioBufferShort.get(i);
                    if(s> maxAmplitude.value){
                        maxAmplitude.value = s;
                    }
                    s = (short) Math.min(Math.max(s * gain, Short.MIN_VALUE), Short.MAX_VALUE);
                    audioBufferShort.put(i, s);
                }
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                FloatBuffer audioBufferFloat = buffer.asFloatBuffer();
                read = record.read(buffer, readBufferSize);
                for(int i =0; i < read / 4; i++){
                    float f = audioBufferFloat.get(i);
                    if(f>maxAmplitude.value){
                        maxAmplitude.value = (int) f;
                    }
                    f = (short) Math.min(Math.max(f * gain, Float.MIN_VALUE), Float.MAX_VALUE);
                    audioBufferFloat.put(i, f);
                }
                break;
        }
    }

    static abstract class AudioConverterThread extends Thread{
        private ByteArrayInputStream bytesIn;
        private ReadableByteChannel readChannel;
        private MediaCodec codec;
        private MediaMuxer muxer;
        private int audioTrackIndex;
        private boolean inputEndofStream;
        private boolean outputEndofStream;
        private final long TIMEOUT = 5000;
        private long presentationTimeUs;
        private MediaCodec.BufferInfo info;
        private MediaFormat outputformat;
        private int bufferIndex;
        private long lastPresentationTimeUs;
        short progressRate;
        short lastProgress;
        private long totalTime;

        private byte[] audioData;
        private int sampleRate;
        private int bitRate;
        private int channelCount;
        private String saveDirectory;
        private String name;

        /**
         *
         * @param group Group this thread belongs to
         * @param name  Basename of the audio file
         * @param audioData Array containing audio data to be put into the encoder
         * @param sampleRate preferred sample rate
         * @param bitRate preferred bit rate for output
         * @param saveDirectory Location where new file with encoded will be saved
         */
        AudioConverterThread(ThreadGroup group,
                             String name,
                             byte[] audioData,
                             int sampleRate,
                             int bitRate,
                             int channelCount,
                             String saveDirectory) {
            super(group, name);
            this.name = name;
            this.audioData = audioData;
            this.sampleRate = sampleRate;
            this.bitRate = bitRate;
            this.channelCount = channelCount;
            this.saveDirectory = saveDirectory;
        }

        @Override
        public final void run() {
            try {
                bytesIn = new ByteArrayInputStream(audioData);
                readChannel = Channels.newChannel(bytesIn);
                totalTime = ((long)(audioData.length)/2) * 1000000 / sampleRate;
                File m4aFile = new File(saveDirectory, name + ".m4a");
                MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);

                outputformat = new MediaFormat();
                outputformat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

                outputformat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
                outputformat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
                outputformat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
                //outputformat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                //outputformat.setInteger(MediaFormat.KEY_AAC_SBR_MODE, 0);
                Log.i(LOG, "channel count=" + channelCount);

                codec = MediaCodec.createByCodecName(codecList.findEncoderForFormat(outputformat));
                Log.i(LOG, codec.getName());
                codec.configure(outputformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                Log.i(LOG, codec.getOutputFormat().toString());

                muxer = new MediaMuxer(m4aFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                audioTrackIndex = 0;
                inputEndofStream = false;
                outputEndofStream = false;
                presentationTimeUs = 0;
                lastPresentationTimeUs = 0;
                info = new MediaCodec.BufferInfo();
                codec.start();

                while (!inputEndofStream && !outputEndofStream) {
                    bufferIndex = -1;
                    while (!inputEndofStream) {
                        bufferIndex = codec.dequeueInputBuffer(TIMEOUT);
                        if (bufferIndex == -1) break;
                        ByteBuffer input = codec.getInputBuffer(bufferIndex);
                        int read = readChannel.read(input);
                        if (read == -1) {
                            Log.i(LOG, "encoder input reached end of stream");
                            codec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEndofStream = true;
                            readChannel.close();
                        } else {
                            codec.queueInputBuffer(bufferIndex, 0, read, presentationTimeUs, 0);
                            /*
                            Sample rate is the number of individual data recorded in a second.
                            One sample was acquired as short data and thus it is equivalent to 2 bytes of data.
                            */
                            presentationTimeUs += (((long) read) / 2) * 1000000 / sampleRate;
                        }
                    }
                    bufferIndex = -1;
                    while (true) {
                        bufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT);
                        if (bufferIndex >= 0) {
                            ByteBuffer output = codec.getOutputBuffer(bufferIndex);
                            if (output != null && info.presentationTimeUs > lastPresentationTimeUs) {
                                muxer.writeSampleData(audioTrackIndex, output, info);
                                progressRate = (short) (((double)info.presentationTimeUs/(double)totalTime)*100);
                                if(progressRate>lastProgress) {
                                    updateConversionProgress();
                                }
                                lastProgress = progressRate;
                            }
                            codec.releaseOutputBuffer(bufferIndex, false);
                            lastPresentationTimeUs = info.presentationTimeUs;
                        } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            /*
                            getOutputFormat is called here since codec's outputformat is only completely initialized after the first encoding.
                            Code Specific Data is added to the new format. Using MediaMuxer will throw an exception for having missing CSD in output format.
                             */
                            Log.i(LOG, codec.getOutputFormat().toString());
                            audioTrackIndex = muxer.addTrack(codec.getOutputFormat());
                            muxer.start();
                        } else break;
                    }
                    if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        outputEndofStream = true;
                        Log.i(LOG, "encoder ouput reached end of stream");
                    }
                }
                muxer.stop();
                muxer.release();
                codec.stop();
                codec.release();
                progressRate = 100;
                updateConversionProgress();
                onFinished();
                Log.i(LOG, "sucessfully converted raw PCM data to M4A and saved");
            }catch(IOException e){
                Log.e(LOG, "IOException in conversion of audio to M4A", e);
            }catch(Exception e){
                Log.e(LOG, "Exception in conversion of audio to M4A", e);
            }
        }

        void updateConversionProgress(){}

        void onFinished(){}
    }

    static MediaCodec codec;
    private static int sampleRate;

    static void prepareCodec(int bitRate, int sampleRate, int channelCount){
        try {
            AudioProcessingTools.sampleRate = sampleRate;

            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);

            MediaFormat outputformat = new MediaFormat();
            outputformat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

            outputformat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            outputformat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
            outputformat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            //outputformat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            //outputformat.setInteger(MediaFormat.KEY_AAC_SBR_MODE, 0);

            codec = MediaCodec.createByCodecName(codecList.findEncoderForFormat(outputformat));
            Log.i(LOG, codec.getName());
            codec.configure(outputformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(LOG, codec.getOutputFormat().toString());
        }
        catch(IOException e){
            Log.e(LOG, "IOException in preparing codec", e);
        }catch(Exception e){
            Log.e(LOG, "Exception in preparing codec", e);
        }
    }

    private static MediaMuxer muxer;
    private static int audioTrackIndex;
    private static long presentationTimeUs;
    private static long lastPresentationTimeUs;
    private static Handler codecHandler;

    static void initAudioConversion(final String saveDirectory, final String name){
        try {
            recordingStopped = false;
            File m4aFile = new File(saveDirectory, name + ".m4a");
            muxer = new MediaMuxer(m4aFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            audioTrackIndex = 0;
            presentationTimeUs = 0;
            lastPresentationTimeUs = 0;
            HandlerThread coderThread = new HandlerThread("codec thread");
            coderThread.start();
            codecHandler = new Handler(coderThread.getLooper());
            Log.i(LOG, "successfully prepared codec handler");
            codec.setCallback(new AudioConversionCallback());
            codec.start();
        }
        catch(IOException e){
            Log.e(LOG, "IOException in initializing encoding", e);
        }catch(Exception e){
            Log.e(LOG, "Exception in initializing encoding", e);
        }
    }
    private static ByteBuffer rawAudioBuffer;

    static void addData(final ByteBuffer buffer){
        codecHandler.post(new Runnable() {
            @Override
            public void run() {
                if (rawAudioBuffer != null) {
                    ByteBuffer newBuffer = ByteBuffer.allocateDirect(rawAudioBuffer.remaining() + buffer.remaining());
                    newBuffer.put(rawAudioBuffer).put(buffer);
                    rawAudioBuffer = newBuffer;
                }
                else {
                    rawAudioBuffer = ByteBuffer.allocateDirect(buffer.remaining());
                    rawAudioBuffer.put(buffer);
                }
            }
        });
    }

    private static boolean recordingStopped;
    private static OnEncodingFinishedListener finishedListener;

    private static final class AudioConversionCallback extends MediaCodec.Callback{

        @Override
        public synchronized void onInputBufferAvailable(@NonNull final MediaCodec codec, final int index) {
            codecHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (rawAudioBuffer != null) {
                        ByteBuffer input = codec.getInputBuffer(index);
                        rawAudioBuffer.rewind();
                        int read = 0;
                        if (input != null) {
                            while (input.hasRemaining() && rawAudioBuffer.hasRemaining()) {
                                input.put(rawAudioBuffer.get());
                                read++;
                            }
                        }
                        rawAudioBuffer = rawAudioBuffer.slice();
                        if (read > 0) {
                            codec.queueInputBuffer(index, 0, read, presentationTimeUs, 0);
                            /*
                            Sample rate is the number of individual data recorded in a second.
                            One sample was acquired as short data and thus it is equivalent to 2 bytes of data.
                            */
                            presentationTimeUs += (((long) read) / 2) * 1000000 / sampleRate;
                        } else if (recordingStopped) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else codec.queueInputBuffer(index, 0, 0, 0, 0);
                    } else {
                        codec.queueInputBuffer(index, 0, 0, 0, 0);
                    }
                }
            });
        }

        @Override
        public void onOutputBufferAvailable(@NonNull final MediaCodec codec, final int index, @NonNull final MediaCodec.BufferInfo info) {
            codecHandler.post(new Runnable() {
                @Override
                public void run() {
                    ByteBuffer output = codec.getOutputBuffer(index);
                    if (output != null && info.presentationTimeUs > lastPresentationTimeUs) {
                        muxer.writeSampleData(audioTrackIndex, output, info);
                    }
                    codec.releaseOutputBuffer(index, false);
                    lastPresentationTimeUs = info.presentationTimeUs;
                    if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        muxer.stop();
                        muxer.release();
                        muxer = null;
                        codec.stop();
                        finishedListener.onEncodingFinished();
                    }
                }
            });
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.e(LOG, "Error converting audio", e);
        }

        @Override
        public void onOutputFormatChanged(@NonNull final MediaCodec codec, @NonNull MediaFormat format) {
            codecHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioTrackIndex = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                }
            });
        }
    }

    static void notifyRecordingEnded(){
        recordingStopped = true;
    }

    interface OnEncodingFinishedListener{
        void onEncodingFinished();
    }

    static void setOnEncodingFinishedListener(OnEncodingFinishedListener onEncodingFinishedListener){
        finishedListener = onEncodingFinishedListener;
    }
}
