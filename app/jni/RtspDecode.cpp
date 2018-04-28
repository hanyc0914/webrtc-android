//
// Created by 韩亚超 on 2018/4/20.
//
//
// Created by 韩亚超 on 2018/4/20.
//
#include <jni.h>
#include <android/log.h>
#define  LOG_TAG    "jniTest"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

extern "C" {

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavfilter/avfilter.h>
#include <libswscale/swscale.h>
#include "FFmpeg.h"

}
const char * rtspURL;
FFmpeg * ffmpeg;
extern "C" {

void Java_org_appspot_apprtc_PanoVideoCapturer_initialWithUrl(JNIEnv *env,
		jobject thisz, jstring url) {
	rtspURL = env->GetStringUTFChars(url, NULL);
	LOGI("%s", rtspURL);
	ffmpeg = new FFmpeg();
	ffmpeg->initial((char *) rtspURL, env);

	jclass cls = env->GetObjectClass(thisz);
	jfieldID int_width = env->GetFieldID(cls, "width", "I");
	jfieldID int_height = env->GetFieldID(cls, "height", "I");
	jint jwidth = env->GetIntField(thisz, int_width);
	jint jheight = env->GetIntField(thisz, int_height);
	jwidth = ffmpeg->width;
	jheight = ffmpeg->height;
	LOGI("ffmpeg width:%d................\n",ffmpeg->width);
	LOGI("ffmpeg height:%d...............\n",ffmpeg->height);
	env->SetIntField(thisz, int_width, jwidth);
	env->SetIntField(thisz, int_height, jheight);
   	//jmethodID mid = env->GetMethodID(cls, "setCodecInfo", "(II)V"); //调用java的方法
   	//env->CallVoidMethod(thisz, mid, (int) ffmpeg->width, (int) ffmpeg->height);
}

void Java_org_appspot_apprtc_PanoVideoCapturer_startDecode(JNIEnv *env, jobject thisz, jobject obs) {
	ffmpeg->h264Decodec(env, obs);
}
}

