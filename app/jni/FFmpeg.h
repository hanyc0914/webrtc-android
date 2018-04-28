//
// Created by 韩亚超 on 2018/4/20.
//

#ifndef ANDROIDAPP1_FFMPEG_H
#define ANDROIDAPP1_FFMPEG_H

#include <jni.h>
#include <stdio.h>


#include <android/log.h>
#define  LOG_TAG    "jniTest"


extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavfilter/avfilter.h>
#include <libswscale/swscale.h>
#include <libavutil/imgutils.h>
#include <stdint.h>
}
class FFmpeg {
public:
	FFmpeg();
	int initial(char * url, JNIEnv *e);
	int h264Decodec(JNIEnv *env, jobject thisz);
	virtual ~FFmpeg();
	friend class Video;
	int width;
	int height;
	int rotation;
	uint8_t *buffer;
	int length;
private:
	AVFormatContext *pFormatCtx;
	AVCodecContext *pCodecCtx;
	AVFrame *pFrame;
	AVFrame *pFrameYUV;
	AVPacket packet;
	SwsContext * pSwsCtx;
	int videoStream;

	char * rtspURL;
	JNIEnv *env;
};

#endif //ANDROIDAPP1_FFMPEG_H
