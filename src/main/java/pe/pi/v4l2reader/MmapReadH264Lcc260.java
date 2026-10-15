package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import pe.pi.amlh264enc.Encoder;

/**
 *
 * @author thp access the Extended controls for the LCC260 h264 usb encoder via
 * UVC see LCC260_UVC_XU_Reference.pdf
 *
 */
public class MmapReadH264Lcc260 extends MmapReadH264 implements Encoder {

    static byte XU_UNIT_ID = 4;
    /* LCC260 Extension Unit ID */
    static byte XU_SWITCH_TAG = (byte) 0x9A;
    static byte UVC_SET_CUR = 0x01;
    static byte UVC_GET_CUR = (byte) 0x81;

    /* bTag of the switch command */
    int XU_BUF_MAX = 64;
    /* Control Selectors (CS) */
    static byte CS_XU_H264_CTRL = 0x02;
    static byte CS_XU_MJPG_CTRL = 0x03;
    static byte CS_XU_OSD_CTRL = 0x04;
    static byte CS_XU_IMAGE_CTRL = 0x06;
    static byte CS_XU_OEM_CTRL = 0x0B;
    /* XU_H264_CTRL sub-functions */
    static byte H264_LIMIT = 0x01;
    static byte H264_BITRATE_CTRL = 0x02;
    static byte H264_GOP = 0x03;
    static byte H264_IFRAME = 0x04;
    static byte H264_SEI = 0x05;
    static byte H264_MODE = 0x06;
    static byte H264_QP = 0x07;
    /* XU_IMAGE_CTRL sub-functions */
    static int IMAGE_MIRROR = 0x01;
    static byte UVC_GET_LEN = (byte) 0x85;
    static byte H264_MODE_CBR = 1;
    static byte H264_MODE_VBR = 2;
    //protected int controlDev;

    public MmapReadH264Lcc260(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate);
        Log.info("managing encoder outputting h264 from " + dev);
    }

    /*
    /* One raw XU request through the uvcvideo driver /
static int xu_query(int fd, uint8_t cs, uint8_t query,
uint16_t size, uint8_t *data)
{
struct uvc_xu_control_query q = {
LCC260 UVC Extension Unit reference Page 2
.unit = XU_UNIT_ID, .selector = cs, .query = query,
.size = size, .data = data,
};
if (ioctl(fd, UVCIOC_CTRL_QUERY, &q) < 0) {
fprintf(stderr, "XU cs=0x%02X query=0x%02X: %s\n",
cs, query, strerror(errno));
return -1;
}
return 0;
}
     */
    int xu_query(byte cs, byte query, short size, MemorySegment data) throws Throwable {
        MemorySegment q = arena.allocate(uvc_xu_control_query);
        q.set(JAVA_BYTE, uvc_xu_control_query.byteOffset(groupElement("unit")), XU_UNIT_ID);
        q.set(JAVA_BYTE, uvc_xu_control_query.byteOffset(groupElement("selector")), cs);
        q.set(JAVA_BYTE, uvc_xu_control_query.byteOffset(groupElement("query")), query);
        q.set(JAVA_SHORT, uvc_xu_control_query.byteOffset(groupElement("size")), size);
        q.set(ADDRESS, uvc_xu_control_query.byteOffset(groupElement("data")), data);
        Log.verb("controlDev =" + videoDev);
        int result = (int) ioctl.invoke(videoDev, UVCIOC_CTRL_QUERY, q);
        Log.verb("xu_query(" + (int) cs + "," + (int) query + "," + size + ")->" + result);
        return result;

    }

    /*
    / GET_LEN -> wCS_LEN (2 bytes, little-endian per UVC spec) 
static int xu_get_len(int fd, uint8_t cs)
{
uint8_t b[2] = {0};
if (xu_query(fd, cs, UVC_GET_LEN, 2, b) < 0)
return -1;
int len = b[0] | (b[1] << 8);
return (len > 0 && len <= XU_BUF_MAX) ? len : -1;
}
    
     */
    int xu_get_len(byte cs) throws Throwable {
        MemorySegment b = arena.allocate(2);
        int ret;
        if (xu_query(cs, UVC_GET_LEN, (short) 2, b) < 0) {
            throw new Exception("unexpected length ");
        } else {
            ByteBuffer bb = b.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            int len = bb.getChar();
            ret = (len > 0 && len <= XU_BUF_MAX) ? len : -1;
        }
        return ret;
    }

    /*
// GET_LEN + switch command (bTag=0x9A, bSubFunction=sub).
    //Returns wCS_LEN, to be used for the following SET_CUR/GET_CUR. 
static int xu_select(int fd, uint8_t cs, uint8_t sub)
{
int len = xu_get_len(fd, cs);
if (len < 2)
return -1;
uint8_t b[XU_BUF_MAX] = {0};
b[0] = XU_SWITCH_TAG;
b[1] = sub;
if (xu_query(fd, cs, UVC_SET_CUR, len, b) < 0)
return -1;
return len;
} */
    int xu_select(byte cs, byte sub) throws Throwable {
        int len = xu_get_len(cs);
        if (len < 2) {
            Log.debug("length was " + len + "expecting 2 or more");
            throw new Exception("unexpected length");
        }
        MemorySegment b = arena.allocate(XU_BUF_MAX);
        b.set(JAVA_BYTE, 0, XU_SWITCH_TAG);
        b.set(JAVA_BYTE, 1, sub);

        if (xu_query(cs, UVC_SET_CUR, (short) len, b) < 0) {
            return -1;
        }
        return len;
    }

    /*
/* Write a sub-function payload (zero-padded to wCS_LEN) 
static int xu_set(int fd, uint8_t cs, uint8_t sub,
const uint8_t *data, int n)
{
int len = xu_select(fd, cs, sub);
if (len < n)
return -1;
uint8_t b[XU_BUF_MAX] = {0};
memcpy(b, data, n);
return xu_query(fd, cs, UVC_SET_CUR, len, b);
}
     */
    int xu_set(byte cs, byte sub, ByteBuffer data, int n) throws Throwable {
        int len = xu_select(cs, sub);
        if (len < n) {
            return -1;
        }
        MemorySegment b = arena.allocate(XU_BUF_MAX);
        b.asByteBuffer().put(0, data, 0, n);
        return xu_query(cs, UVC_SET_CUR, (short) len, b);
    }

    /*
/* Read a sub-function payload (first n bytes of wCS_LEN) 
static int xu_get(int fd, uint8_t cs, uint8_t sub, uint8_t *data, int n)
{
int len = xu_select(fd, cs, sub);
if (len < n)
return -1;
uint8_t b[XU_BUF_MAX] = {0};
if (xu_query(fd, cs, UVC_GET_CUR, len, b) < 0)
return -1;
memcpy(data, b, n);
return 0;
}
     */
    ByteBuffer xu_get(byte cs, byte sub, int n) throws Throwable {
        int len = xu_select(cs, sub);
        if (len < n) {
            throw new Exception("invalid length");
        }
        MemorySegment b = arena.allocate(XU_BUF_MAX);
        if (xu_query(cs, UVC_GET_CUR, (short) len, b) < 0) {
            throw new Exception("invalid UVC_GET_CUR");
        }
        ByteBuffer ret = ByteBuffer.allocate(len);
        var ba = b.asByteBuffer();
        ret.put(0, ba, 0, len);
        return ret;
    }

    /*
    int h264_set_gop(int fd, uint16_t gop)
{
if (gop < 1 || gop > 1023)
return -1;
uint8_t b[2] = { gop & 0xFF, gop >> 8 };
return xu_set(fd, CS_XU_H264_CTRL, H264_GOP, b, 2);
}
     */
    void h264_set_gop(char gop) throws Throwable {
        ByteBuffer b = ByteBuffer.allocate(2);
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.putChar(gop);
        xu_set(CS_XU_H264_CTRL, H264_GOP, b, 2);
    }

    /*
    
{
int h264_get_gop(int fd, uint16_t *gop)
uint8_t b[2];
if (xu_get(fd, CS_XU_H264_CTRL, H264_GOP, b, 2) < 0)
return -1;
*gop = b[0] | (b[1] << 8);
return 0;
}
     */
    int h264_get_gop() throws Throwable {
        ByteBuffer b = xu_get(CS_XU_H264_CTRL, H264_GOP, 2);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b.getChar(0);
    }

    void h264_force_iframe() throws Throwable {
        ByteBuffer b = ByteBuffer.allocate(1);
        b.put((byte) 1);
        xu_set(CS_XU_H264_CTRL, H264_IFRAME, b, 1);
    }

    void h264_set_mode(byte mode) throws Throwable {
        if (mode != H264_MODE_CBR && mode != H264_MODE_VBR) {
            throw new IllegalArgumentException("invalid Bitrate mode " + mode);
        }
        ByteBuffer b = ByteBuffer.allocate(1);
        b.put((byte) mode);
        xu_set(CS_XU_H264_CTRL, H264_MODE, b, 1);
    }

    byte h264_get_mode() throws Throwable {
        ByteBuffer b = xu_get(CS_XU_H264_CTRL, H264_MODE, 1);
        return b.get(0);
    }

    int h264_get_bitrate() throws Throwable {
        ByteBuffer b = xu_get(CS_XU_H264_CTRL, H264_BITRATE_CTRL, 3);
        ByteBuffer b2 = ByteBuffer.allocate(4);
        b2.put(1, b, 0, 3);
        b2.order(ByteOrder.BIG_ENDIAN);
        return b2.getInt(0);
    }

    void h264_set_bitrate(int bps) throws Throwable {

        if (bps == 0 || bps > 0xFFFFFF) {
            throw new IllegalArgumentException("invalid Bitrate  " + bps);
        }
        ByteBuffer b = ByteBuffer.allocate(4);
        b.order(ByteOrder.BIG_ENDIAN);
        b.putInt(0, bps);
        var b2 = b.slice(1, 3);

        xu_set(CS_XU_H264_CTRL, H264_BITRATE_CTRL, b2, 3);
    }

    public void tweak() throws Throwable {

            Log.debug("tweaking controls ");
            Log.debug("h264 mode was " + (int) h264_get_mode());
            Log.debug("set cbr");
            h264_set_mode(H264_MODE_CBR);
            Log.debug("h264 mode is " + (int) h264_get_mode());
            h264_set_gop((char) 240);
            Log.info("h264 gop is " + (int) h264_get_gop());


    }

    @Override
    public void keyFrameReq() {
        try {
            Log.info("force keyframe from capture device");
            h264_force_iframe();
        } catch (Throwable t) {
            Log.error("can't request keyframe because " + t.getLocalizedMessage());
        }
    }

    @Override
    public void setNewBitrate(int bwe) {
        int bitrate = bwe * 1000;
        try {
            h264_set_bitrate(bitrate);
            Log.debug("h264 bitrate is " + h264_get_bitrate());
        } catch (Throwable t) {
            Log.error("can't set bitrate  because " + t.getLocalizedMessage());
        }
    }

    @Override
    public ByteBuffer encode(ByteBuffer in) {
        return in;
    }

    public static void main(String[] args) {
        Log.setLevel(Log.VERB);
        String adev = "/dev/video2";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            var mmap = new MmapReadH264Lcc260(adev, 1920, 1080, 30) {
                @Override
                public int setup(int framerate, int format) throws Throwable {
                    // Define native methods

                    Log.debug("in fake setup()");

                    Log.debug("common functions allocated");

                    MemorySegment devName = arena.allocateFrom(path.toString());

                    int fd = (int) open.invoke(devName, O_RDWR);
                    if (fd < 0) {
                        Log.error("Failed to open video device");
                        return fd;
                    }
                    videoDev = fd;
                    Log.debug("opened " + path);
                    return fd;
                }

            };
            mmap.tweak();
            Log.debug("h264 mode is " + (int) mmap.h264_get_mode());
            Log.debug("set vbr");
            mmap.h264_set_mode(H264_MODE_VBR);
            Log.debug("h264 mode is " + (int) mmap.h264_get_mode());
            Log.debug("set cbr");
            mmap.h264_set_mode(H264_MODE_CBR);
            Log.debug("h264 mode is " + (int) mmap.h264_get_mode());
            Log.debug("h264 bitrate is " + mmap.h264_get_bitrate());
            Log.debug("set bitrate to 4Mbit/s");
            mmap.h264_set_bitrate(4000000);
            Log.debug("h264 bitrate is " + mmap.h264_get_bitrate());
            Log.debug("set bitrate to 1Mbit/s");
            mmap.h264_set_bitrate(1000000);
            Log.debug("h264 bitrate is " + mmap.h264_get_bitrate());
            Log.debug("h264 gop is " + (int) mmap.h264_get_gop());
            Log.debug("set GOP 30 ");
            mmap.h264_set_gop((char) 30);
            Log.debug("h264 gop is " + (int) mmap.h264_get_gop());
            Log.debug("set GOP 240 ");
            mmap.h264_set_gop((char) 240);
            Log.debug("h264 gop is " + (int) mmap.h264_get_gop());
            Log.debug("force keyframe");
            mmap.h264_force_iframe();
            Log.debug("force keyframe done ");

        } catch (Throwable t) {
            Log.error("cproblem with" + adev);
            if (Log.getLevel() > Log.INFO) {
                t.printStackTrace();
            }
        }

    }
}
