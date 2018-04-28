//
// Created by 韩亚超 on 2018/4/20.
//

#include "FFmpeg.h"

#ifdef ANDROID
#include <android/log.h>
#define LOGE(format, ...)  __android_log_print(ANDROID_LOG_ERROR, "(>_<)", format, ##__VA_ARGS__)
#define LOGI(format, ...)  __android_log_print(ANDROID_LOG_INFO,  "(^_^)", format, ##__VA_ARGS__)
#else
#define LOGE(format, ...)  printf("(>_<) " format "\n", ##__VA_ARGS__)
#define LOGI(format, ...)  printf("(^_^) " format "\n", ##__VA_ARGS__)
#endif

FFmpeg::FFmpeg() {
	pCodecCtx = NULL;
	videoStream = -1;
}

FFmpeg::~FFmpeg() {
	sws_freeContext(pSwsCtx);
	avcodec_close(pCodecCtx);
	avformat_close_input(&pFormatCtx);
}

int FFmpeg::initial(char * url, JNIEnv * e) {
	env = e;
	rtspURL = url;
	AVCodec *pCodec;

	av_register_all();
	avformat_network_init();
	pFormatCtx = avformat_alloc_context();
	pFrame = av_frame_alloc();
	pFrameYUV = av_frame_alloc();

	AVDictionary* options = NULL;
    av_dict_set(&options, "rtsp_transport", "tcp", 0);

	int err = avformat_open_input(&pFormatCtx, rtspURL, NULL, &options);
	if (err < 0) {
	    char buf[1024];
        av_strerror(err, buf, 1024);
        LOGE("error %s: %d(%s)",rtspURL, err, buf);
        LOGE("Couldn't open input stream.\n");
		return -1;
	}
	LOGI("open file successed.\n");
	if (avformat_find_stream_info(pFormatCtx,NULL) < 0) {
		LOGE("Couldn't find stream information.\n");
		return -1;
	}
	LOGI("Find stream information successfully.\n");

	int i = 0;
	videoStream = -1;
	for (i = 0; i < pFormatCtx->nb_streams; i++) {
		if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
			videoStream = i;
			break;
		}
	}
	if (videoStream == -1) {
	    LOGE("Find video stream failed.\n");
		return -1;
	}
	LOGI("Find video stream successed.\n");
	pCodecCtx = pFormatCtx->streams[videoStream]->codec;
	pCodec = avcodec_find_decoder(pCodecCtx->codec_id);

	width = pCodecCtx->width;
	height = pCodecCtx->height;
	LOGI("width:%d height:%d.\n",width,height);

    length = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height,1);
    buffer=(unsigned char *)av_malloc(length);
    av_image_fill_arrays(pFrameYUV->data, pFrameYUV->linesize,buffer,
            AV_PIX_FMT_YUV420P,width,height,1);

	pSwsCtx = sws_getContext(width, height, pCodecCtx->pix_fmt, width, height,
			AV_PIX_FMT_YUV420P, SWS_BICUBIC, 0, 0, 0);

	if (pCodec == NULL) {
		LOGE("Couldn't find Codec.\n");
		return -1;
	}
	LOGI("Find codec successfully.\n");
	if(avcodec_open2(pCodecCtx, pCodec,NULL)<0){
        LOGE("Couldn't open codec.\n");
        return -1;
    }
	LOGI("Initial successfully");
	return 0;
}

int FFmpeg::h264Decodec(JNIEnv *env, jobject thisz) {
	int frameFinished = 0;
	int ret = -1;
	void *temp;
	//jstring input = env->NewStringUTF(rtspURL);
	jclass cls = env->GetObjectClass(thisz);
	//jmethodID construction_id = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;)V");
    jmethodID onframe = env->GetMethodID(cls, "onFrameDecode", "([B)V");
    //jobject obj1 = env->NewObject(cls, construction_id, input);
    jbyteArray retArray = env->NewByteArray(length);
    //void *temp;
    //jfieldID byte_fieldID = env->GetFieldID(cls, "buffer", "[B");
    //jbyteArray jbuffer = (jbyteArray) env->GetObjectField(thisz, byte_fieldID);
    //jbyte* jbufferbyte = (jbyte*) buffer;
	while (av_read_frame(pFormatCtx, &packet) >= 0) {
		if (packet.stream_index == videoStream) {
			ret = avcodec_decode_video2(pCodecCtx, pFrame, &frameFinished, &packet);
			if(ret < 0){
			    LOGE("Decode Error.\n");
			    return -1;
			}
			if (frameFinished) {
				LOGI("***************ffmpeg decodec*******************\n");
				int rs = sws_scale(pSwsCtx,
						(const uint8_t* const *) pFrame->data, pFrame->linesize,
						0, height, pFrameYUV->data, pFrameYUV->linesize);
			    if(pFrameYUV->data[0][0] == buffer[0]){
			        LOGI("******************equal********************\n");
			    }else{
			        LOGI("******************not equal****************\n");
			    }
				LOGI("***************sws_scale************************\n");
                temp = env->GetPrimitiveArrayCritical((jarray)retArray, 0);
                LOGI("***************get array************************\n");
			    memcpy(temp, buffer, length);
			    LOGI("***************mem_copy************************\n");
			    env->ReleasePrimitiveArrayCritical(retArray, temp, 0);
			    LOGI("***************release array***************\n");
				env->CallVoidMethod(thisz, onframe, retArray);
                LOGI("***************ffmpeg decodec one frame*******************\n");
				//if (rs == -1) {
					//LOGE("__________Can open to change to des imag_____________e\n");
					//return -1;
				//}
			}
		}
		av_free_packet(&packet);
	}
	while(1){
	    ret = avcodec_decode_video2(pCodecCtx, pFrame, &frameFinished, &packet);
	    if(ret < 0){
	        break;
	    }
	    if(!frameFinished){
	        break;
	    }
	    int rs = sws_scale(pSwsCtx, (const uint8_t* const *) pFrame->data, pFrame->linesize,
        				   0, height, pFrameYUV->data, pFrameYUV->linesize);
        memcpy(temp, buffer, length);
        env->ReleasePrimitiveArrayCritical(retArray, temp, 0);
       	env->CallVoidMethod(thisz, onframe, retArray);
	}
	return 1;
}