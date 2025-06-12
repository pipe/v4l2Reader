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
    final static int V4L2_MEMORY_MMAP = 1;
    final static int V4L2_MEMORY_DMABUF = 4;
    GroupLayout v4l2_pix_format = MemoryLayout.structLayout(
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
    GroupLayout v4l2_requestbuffers = MemoryLayout.structLayout(
            JAVA_INT.withName("count"),
            JAVA_INT.withName("type"),
            JAVA_INT.withName("memory"),
            JAVA_INT.withName("capabilities"),
            JAVA_INT.withName("flags")// lie - last 3 bytes are reserved.
    );
    GroupLayout v4l2_buffer = MemoryLayout.structLayout(
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
    GroupLayout v4l2_format = MemoryLayout.structLayout(
            JAVA_INT.withName("type"),
            MemoryLayout.paddingLayout(4), // Align union to 8 bytes
            v4l2_pix_format.withName("pix"),
            MemoryLayout.paddingLayout(200 - v4l2_pix_format.byteSize()) // pad to full union size
    );
}
