/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 *
 * @author thp
 */
public class V4l2Structs {

    final static int V4L2_BUF_TYPE_VIDEO_CAPTURE = 1;
    final static int V4L2_FIELD_NONE = 1;
    final static int V4L2_PIX_FMT_NV12 = 0x3231564E; // 'NV12'
    final static int V4L2_PIX_FMT_YUYV = 0x56595559;
    static final int V4L2_PIX_FMT_MJPEG = (int) 1196444237L;
    static final int V4L2_PIX_FMT_JPEG = (int) 1195724874L;

    static final int V4L2_CID_BASE = 0x00980900;
    static final int V4L2_CID_BRIGHTNESS = (V4L2_CID_BASE + 0);
    static final int V4L2_CID_CONTRAST = (V4L2_CID_BASE + 1);
    static final int V4L2_CID_SATURATION = (V4L2_CID_BASE + 2);
    static final int V4L2_CID_HUE = (V4L2_CID_BASE + 3);
    static final int V4L2_CID_BLACK_LEVEL = (V4L2_CID_BASE + 11);/* Deprecated */
    static final int V4L2_CID_AUTO_WHITE_BALANCE = (V4L2_CID_BASE + 12);
    static final int V4L2_CID_DO_WHITE_BALANCE = (V4L2_CID_BASE + 13);
    static final int V4L2_CID_GAMMA = (V4L2_CID_BASE + 16);
   // static final int V4L2_CID_EXPOSURE = (V4L2_CID_BASE + 17);
    static final int V4L2_CID_AUTOGAIN = (V4L2_CID_BASE + 18);
    static final int V4L2_CID_GAIN = (V4L2_CID_BASE + 19);
    static final int V4L2_CID_WHITE_BALANCE_TEMPERATURE = (V4L2_CID_BASE + 26);
    static final int V4L2_CID_AE = 0x009a0901; // cheating....
    static final int V4L2_isp_ae_roi = 0x00f0f025; // really cheating
    static final int V4L2_CID_EXPOSURE =0x009a0902; // likewise...

    // struct v4l2_control {
    //     __u32 id;
    //     __s32 value;
    // };
    final static int V4L2_MEMORY_MMAP = 1;
    final static int V4L2_MEMORY_DMABUF = 4;
    public static GroupLayout v4l2_pix_format = MemoryLayout.structLayout(
            JAVA_INT.withName("width"),
            JAVA_INT.withName("height"),
            JAVA_INT.withName("pixelformat"),
            JAVA_INT.withName("field"),
            JAVA_INT.withName("bytesperline"),
            JAVA_INT.withName("sizeimage"),
            JAVA_INT.withName("colorspace"),
            JAVA_INT.withName("priv"),
            JAVA_INT.withName("flags"),
            JAVA_INT.withName("ycbcr_enc"),
            JAVA_INT.withName("quantization"),
            JAVA_INT.withName("xfer_func")
    );
    public static GroupLayout v4l2_requestbuffers = MemoryLayout.structLayout(
            JAVA_INT.withName("count"),
            JAVA_INT.withName("type"),
            JAVA_INT.withName("memory"),
            JAVA_INT.withName("capabilities"),
            JAVA_INT.withName("flags")// lie - last 3 bytes are reserved.
    );
    public static GroupLayout v4l2_buffer = MemoryLayout.structLayout(
            JAVA_INT.withName("index"),
            JAVA_INT.withName("type"),
            JAVA_INT.withName("bytesused"),
            JAVA_INT.withName("flags"),
            JAVA_INT.withName("field"),
            MemoryLayout.paddingLayout(4),
            MemoryLayout.structLayout(JAVA_LONG, JAVA_LONG).withName("timestamp"),
            MemoryLayout.structLayout(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT).withName("timecode"),
            JAVA_INT.withName("sequence"),
            JAVA_INT.withName("memory"),
            JAVA_LONG.withName("m_offset"),
            JAVA_INT.withName("length"),
            JAVA_INT.withName("reserved2"),
            JAVA_INT.withName("reserved")
    );
    public static GroupLayout v4l2_format = MemoryLayout.structLayout(
            JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4), // Align union to 8 bytes
            v4l2_pix_format.withName("pix"),
            MemoryLayout.paddingLayout(200 - v4l2_pix_format.byteSize()) // pad to full union size
    );
    /*
    struct v4l2_fract {
        __u32   numerator;
        __u32   denominator;
};
     */
 /*  
    struct v4l2_streamparm {
        __u32    type;                  // enum v4l2_buf_type
        union {
                struct v4l2_captureparm capture;
                struct v4l2_outputparm  output;
                __u8    raw_data[200];  // user-defined 
        } parm;
};
    struct v4l2_captureparm {
        __u32              capability;    //  Supported modes 
        __u32              capturemode;   //  Current mode 
        struct v4l2_fract  timeperframe;  //  Time per frame in seconds 
        __u32              extendedmode;  //  Driver-specific extensions 
        __u32              readbuffers;   //  # of buffers for read 
        __u32              reserved[4];
};
     */
    final public static GroupLayout v4l2_fract = MemoryLayout.structLayout(
            JAVA_INT.withName("numerator"),
            JAVA_INT.withName("denominator")
    );
    public static GroupLayout v4l2_capture_streamparm = MemoryLayout.structLayout(
            JAVA_INT.withName("type"),
            JAVA_INT.withName("capability"),
            JAVA_INT.withName("capturemode"),
            v4l2_fract.withName("timeperframe"),
            JAVA_INT.withName("extendedmode"),
            JAVA_INT.withName("readbuffers"),
            MemoryLayout.paddingLayout(16),// reserved
            MemoryLayout.paddingLayout(200)// reserved
    );
    // struct v4l2_control {
    //     __u32 id;
    //     __s32 value;
    // };

    public static final GroupLayout v4l2_control
            = MemoryLayout.structLayout(
                    JAVA_INT.withName("id"),
                    JAVA_INT.withName("value"));

}
