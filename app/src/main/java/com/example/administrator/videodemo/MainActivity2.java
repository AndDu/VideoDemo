package com.example.administrator.videodemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity2 extends AppCompatActivity {


    private static final String VIDEO_TYPE = "video/";
    private static final String AUDIO_TYPE = "audio/";
    public final static int TIMEOUT_USEC = 10000;
    private boolean VERBOSE = true;
    private TextureView surface;
    private String url = Environment.getExternalStorageDirectory()
            .getPath() + "/aonlinevideo/1.mp4";
    protected static final String TAG = "MainActivity2";
    // Declare this here to reduce allocations.
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    // May be set/read by different threads.
    private volatile boolean mIsStopRequested;
    private boolean isPlaying=true;
    private boolean cancel;
    private boolean isPause;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_surface);
        surface = findViewById(R.id.surface);
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    playWithUrl();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
        new AudioDecodeThread().start();
    }


    public void playWithUrl() throws IOException {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            /**
             * 创建一个MediaExtractor对象
             */
            extractor = new MediaExtractor();
            /**
             * 设置Extractor的source,这里可以把mp4的url传进来,
             */
            extractor.setDataSource(this, Uri.parse(url), new HashMap<String, String>());
            /**
             * 这里我们需要选择我们要解析的轨道,我们在这个例子里面只解析视频轨道
             */
            int trackIndex = selectTrack(extractor, AUDIO_TYPE);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + url);
            }


            /**
             * 选择视频轨道的索引
             */
            extractor.selectTrack(trackIndex);

            /**
             * 获取轨道的音视频格式,这个格式和Codec有关,可以点击MediaFormat类看看有哪些
             */
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);

            /**
             * 创建一个MediaCodec对象
             */
            decoder = MediaCodec.createDecoderByType(mime);
            /**
             * 设置格式,和视频输出的Surface,开始解码
             */
            decoder.configure(format, new Surface(surface.getSurfaceTexture()), null, 0);
            decoder.start();

            doExtract(extractor, trackIndex, decoder, new SpeedControlCallback());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // release everything we grabbed
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    /**
     * 我们用Extractor获取轨道数量,然后遍历他们,只要找到第一个轨道是Video的就返回
     */
    private static int selectTrack(MediaExtractor extractor, String type) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(type)) {
//                if (VERBOSE) {
//                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
//                }
                return i;
            }
        }

        return -1;
    }


    private void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder, FrameCallback frameCallback) {

        /**
         * 获取MediaCodec的输入队列,是一个数组
         */
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        int inputChunk = 0;
        long firstInputTimeNsec = -1;

        boolean outputDone = false;
        boolean inputDone = false;
        /**
         * 用while做循环
         */
        while (!outputDone) {
            Log.d(TAG, "loop");
            if (mIsStopRequested) {
                Log.d(TAG, "Stop requested");
                return;
            }

            // Feed more data to the decoder.
            /**
             * 不停的输入数据知道输入队列满为止
             */
            if (!inputDone) {
                /**
                 * 这个方法返回输入队列数组可以放数据的位置,即一个索引
                 */
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                /**
                 * 如果输入队列还有位置
                 */
                if (inputBufIndex >= 0) {
                    if (firstInputTimeNsec == -1) {
                        firstInputTimeNsec = System.nanoTime();
                    }
                    ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    /**
                     * 用Extractor读取一个sample的数据,并且放入输入队列
                     */
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    /**
                     * 如果chunk size是小于0,证明我们已经读取完毕这个轨道的数据了。
                     */
                    if (chunkSize < 0) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS");
                    } else {
                        if (extractor.getSampleTrackIndex() != trackIndex) {
                            Log.w(TAG, "WEIRD: got sample from track " +
                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                presentationTimeUs, 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);
                        }
                        inputChunk++;
                        /**
                         * Extractor移动一个sample的位置,下一次再调用extractor.readSampleData()就会读取下一个sample
                         */
                        extractor.advance();
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                /**
                 * 开始把输出队列的数据拿出来,decodeStatus只要不是大于零的整数都是异常的现象,需要处理
                 */
                int decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    throw new RuntimeException(
                            "unexpected result from decoder.dequeueOutputBuffer: " +
                                    decoderStatus);
                } else { // decoderStatus >= 0
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(TAG, "output EOS");
                        outputDone = true;
                    }
                    boolean doRender = (mBufferInfo.size != 0);
                    if (doRender && frameCallback != null) {
                        frameCallback.preRender(mBufferInfo.presentationTimeUs);
                    }
                    /**
                     * 只要我们调用了decoder.releaseOutputBuffer(),
                     * 就会把输出队列的数据全部输出到Surface上显示,并且释放输出队列的数据
                     */
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                }
            }
        }
    }


    /**
     * 音频解码线程
     */
    private class AudioDecodeThread extends Thread {
        private int mInputBufferSize;
        private AudioTrack audioTrack;

        @Override
        public void run() {
            MediaExtractor audioExtractor = new MediaExtractor();
            MediaCodec audioCodec = null;
            try {
                audioExtractor.setDataSource(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat mediaFormat = audioExtractor.getTrackFormat(i);
                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int minBufferSize = AudioTrack.getMinBufferSize(audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT);
                    int maxInputSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    mInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 : maxInputSize;
                    int frameSizeInBytes = audioChannels * 2;
                    mInputBufferSize = (mInputBufferSize / frameSizeInBytes) * frameSizeInBytes;
                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            audioSampleRate,
                            (audioChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO),
                            AudioFormat.ENCODING_PCM_16BIT,
                            mInputBufferSize,
                            AudioTrack.MODE_STREAM);
                    audioTrack.play();
                    try {
                        audioCodec = MediaCodec.createDecoderByType(mime);
                        audioCodec.configure(mediaFormat, null, null, 0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            if (audioCodec == null) {
                if (VERBOSE) {
                    Log.d(TAG, "audio decoder is unexpectedly null");
                }
                return;
            }
            audioCodec.start();
            final ByteBuffer[] buffers = audioCodec.getOutputBuffers();
            int sz = buffers[0].capacity();
            if (sz <= 0) {
                sz = mInputBufferSize;
            }
            byte[] mAudioOutTempBuf = new byte[sz];

            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
            boolean isAudioEOS = false;
            long startMs = System.currentTimeMillis();
            while (!Thread.interrupted() && !cancel) {
                if (isPlaying) {
                    // 暂停
                    if (isPause) {
                        continue;
                    }
                    // 解码
                    if (!isAudioEOS) {
                        isAudioEOS = decodeMediaData(audioExtractor, audioCodec, inputBuffers);
                    }
                    // 获取解码后的数据
                    int outputBufferIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_USEC);
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            if (VERBOSE) {
                                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                            }
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            if (VERBOSE) {
                                Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                            }
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            outputBuffers = audioCodec.getOutputBuffers();
                            if (VERBOSE) {
                                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                            }
                            break;
                        default:
                            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                            // 延时解码，跟视频时间同步
                            decodeDelay(audioBufferInfo, startMs);
                            // 如果解码成功，则将解码后的音频PCM数据用AudioTrack播放出来
                            if (audioBufferInfo.size > 0) {
                                if (mAudioOutTempBuf.length < audioBufferInfo.size) {
                                    mAudioOutTempBuf = new byte[audioBufferInfo.size];
                                }
                                outputBuffer.position(0);
                                outputBuffer.get(mAudioOutTempBuf, 0, audioBufferInfo.size);
                                outputBuffer.clear();
                                if (audioTrack != null)
                                    audioTrack.write(mAudioOutTempBuf, 0, audioBufferInfo.size);
                            }
                            // 释放资源
                            audioCodec.releaseOutputBuffer(outputBufferIndex, false);
                            break;
                    }

                    // 结尾了
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) {
                            Log.d(TAG, "BUFFER_FLAG_END_OF_STREAM");
                        }
                        break;
                    }
                }
            }

            // 释放MediaCode 和AudioTrack
            audioCodec.stop();
            audioCodec.release();
            audioExtractor.release();
            audioTrack.stop();
            audioTrack.release();
        }

    }

    /**
     * 解复用，得到需要解码的数据
     *
     * @param extractor
     * @param decoder
     * @param inputBuffers
     * @return
     */
    private static boolean decodeMediaData(MediaExtractor extractor, MediaCodec decoder, ByteBuffer[] inputBuffers) {
        boolean isMediaEOS = false;
        int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            int sampleSize = extractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isMediaEOS = true;
                if (true) {
                    Log.d(TAG, "end of stream");
                }
            } else {
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                extractor.advance();
            }
        }
        return isMediaEOS;
    }

    /**
     * 解码延时
     *
     * @param bufferInfo
     * @param startMillis
     */
    private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMillis) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }


}
