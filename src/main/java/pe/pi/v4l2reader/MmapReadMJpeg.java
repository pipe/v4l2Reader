package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.io.IOException;
import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import pe.pi.amlh264enc.Encoder;
import pe.pi.sonixcam.SonixCameraAPI;
import pe.pi.turbojpeg.JpegDec;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_EXPOSURE;

/**
 *
 * @author thp
 */
public class MmapReadMJpeg extends MmapRead {

    private final JpegDec decoder;
    private final ByteBuffer i420;
    private final ByteBuffer nv12;
    private SonixV4l2Substitute v4l2Sub;

    public int getBasicV4l2Control(int cont) {
        int ret = -1;
        Log.info("getting v4l2 value for " + (cont - V4L2_CID_BASE));
        try {
            MemorySegment ctrl = arena.allocate(v4l2_control);
            ctrl.set(JAVA_INT, v4l2_control.byteOffset(groupElement("id")), cont);

            int rc = (int) ioctl.invoke(videoDev, VIDIOC_G_CTRL, ctrl);

            if (rc < 0) {
                throw new IOException("VIDIOC_G_CTRL failed");
            }
            ret = ctrl.get(JAVA_INT, v4l2_control.byteOffset(groupElement("value")));

        } catch (Throwable ex) {
            Log.error("can't get value for v4l2ctl " + (cont - V4L2_CID_BASE) + " because " + ex.getMessage());
        }
        return ret;
    }

    public void setBasicV4l2Control(int cont, int value) {
        Log.info("setting v4l2 value for " + (cont - V4L2_CID_BASE) + " to " + value);

        try {
            MemorySegment ctrl = arena.allocate(v4l2_control);

            ctrl.set(JAVA_INT, v4l2_control.byteOffset(groupElement("id")), cont);
            ctrl.set(JAVA_INT, v4l2_control.byteOffset(groupElement("value")), value);

            int rc = (int) ioctl.invoke(videoDev, VIDIOC_S_CTRL, ctrl);

            if (rc < 0) {
                throw new IOException("VIDIOC_S_CTRL failed");
            }
        } catch (Throwable ex) {
            Log.error("can't set value for v4l2ctl " + (cont - V4L2_CID_BASE) + " because " + ex.getMessage());
        }
    }

    public MmapReadMJpeg(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate, V4L2_PIX_FMT_MJPEG);

        decoder = new JpegDec(1920, 1080);
        i420 = ByteBuffer.allocate(2 * 1920 * 1080);
        nv12 = ByteBuffer.allocate(3 * 1920 * 1080 / 2);
        Log.debug("i420 is " + i420.capacity());
        Log.debug("nv12 is " + nv12.capacity());
    }

    @Override
    public ByteBuffer process(ByteBuffer frame) {
        ByteBuffer ret = null;
        try {
            Log.debug("got frame  " + frame.remaining());
            var fbuffer = new byte[frame.remaining()];
            frame.get(fbuffer);
            frame.flip();
            // Files.write(peg, fbuffer, options);

            int r = decoder.decompress(frame, i420);
            //Files.write(f420, i420.array(), options);

            if (r == 0) {
                Log.debug("i420 frame of " + i420.remaining());
                JpegDec.i420ToNV12(i420, nv12, 1920, 1080);
                //Files.write(fnv12, nv12.array(), options);

                Log.debug("nv12 frame of " + nv12.remaining());
                ret = nv12;
            } else {
                Log.error("couldn't de compress jpeg");
            }
        } catch (Throwable t) {
            Log.error("can't transcode because " + t.getMessage());
            t.printStackTrace();
        }
        return ret;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Log.setLevel(Log.VERB);
        String adev = "/dev/video0";
        if (args.length > 0) {
            adev = args[0];
        }
        try {
            var encoder = new Encoder(1920, 1080, 0, false, 30, false);
            OpenOption[] options = {StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE};

            MmapReadMJpeg reader = new MmapReadMJpeg(adev, 1920, 1080, 30) {
                @Override
                public ByteBuffer process(ByteBuffer frame) {
                    ByteBuffer ret = super.process(frame);
                    try {
                        ret = encoder.encode(ret);
                        Log.debug("encoded frame to " + ret.remaining());
                    } catch (Throwable t) {
                        Log.error("can't transcode because " + t.getMessage());
                        t.printStackTrace();
                    }
                    return ret;
                }
            };
            reader.startCap();
            int fno = 0;
            var h264 = Paths.get("100.h264");

            var fout = Files.newOutputStream(h264, options);
            while (fno < 120) {
                if ((fno % 30) == 0) {
                    encoder.keyFrameReq();
                }
                var frame = reader.read();
                fno++;
                byte[] dst = new byte[frame.remaining()];
                Log.debug("copying bytes " + dst.length);
                frame.get(dst);
                fout.write(dst);
            }
            fout.close();
        } catch (Throwable ex) {
            Log.error("can't read from " + adev + " because " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    public V4l2Substitute getV4l2Sub() {
        if (v4l2Sub == null) {
            v4l2Sub = new SonixV4l2Substitute();
        }
        return v4l2Sub;
    }

    class SonixV4l2Substitute implements V4l2Substitute, ControlMapper {

        String sensorName;
        Long ae_roi = 0L;
        Long ae_auto = 1L;
        SonixCameraAPI sapi;
        HashMap<String, V4l2ExtControl> mycontrolmap;
    

        SonixV4l2Substitute() {
            sensorName = "unknown";
            String video = path.getFileName().toString();
            var mpath = Paths.get("/sys/class/video4linux/" + video + "/device/../manufacturer");
            if (Files.isReadable(path)) {
                try {
                    sensorName = Files.readString(mpath);
                    Log.info("sensorname is " + sensorName);
                } catch (IOException ex) {
                    Log.info("can't read from " + mpath.toString());
                }
            } else {
                Log.info("no readable device info at " + mpath.toString());
            }
            sapi = new SonixCameraAPI();

        }

        @Override
        public void setBrightness(Long v) {
            setBasicV4l2Control(V4L2_CID_BRIGHTNESS, v.intValue());
        }

        @Override
        public void setHue(Long v) {
            setBasicV4l2Control(V4L2_CID_HUE, v.intValue());
        }

        @Override
        public void setContrast(Long v) {
            setBasicV4l2Control(V4L2_CID_CONTRAST, v.intValue());
        }

        @Override
        public void setExposure(Long v) {
            setBasicV4l2Control(V4L2_CID_EXPOSURE, v.intValue());
        }

        @Override
        public Long getBrightness() {
            return Long.valueOf(getBasicV4l2Control(V4L2_CID_BRIGHTNESS));
        }

        @Override
        public Long getHue() {
            return Long.valueOf(getBasicV4l2Control(V4L2_CID_HUE));
        }

        @Override
        public Long getContrast() {
            return Long.valueOf(getBasicV4l2Control(V4L2_CID_CONTRAST));
        }

        @Override
        public Long getExposure() {
            return Long.valueOf(getBasicV4l2Control(V4L2_CID_EXPOSURE));
        }

        @Override
        public void setSaturation(Long v) {
            setBasicV4l2Control(V4L2_CID_SATURATION, v.intValue());
        }

        @Override
        public Long getSaturation() {
            return Long.valueOf(getBasicV4l2Control(V4L2_CID_EXPOSURE));
        }

        @Override
        public String getSensorName() {
            return sensorName;
        }

        // these don't work at the moment so we parrot - unless we have a working sonixAPI.
        @Override
        public void setAERoi(Long v) {
            ae_roi = v;
            int x = (int) ((width * ((0xff) & (v >>> 24))) / 128);
            int y = (int) ((height * ((0xff) & (v >>> 16))) / 128);
            int x2 = (int) ((width * ((0xff) & (v >>> 8))) / 128);
            int y2 = (int) ((height * ((0xff) & (v))) / 128);
            int w = x2 - x;
            int h = y2 - y;
            if ((sapi != null) && sapi.inited()) {
                try {
                    sapi.setRoi(x, y, w, h);
                } catch (Throwable t) {
                    Log.warn("Can't set AE");
                }
            }

            //setBasicV4l2Control(V4L2_isp_ae_roi, v.intValue());
        }

        @Override
        public Long getAERoi() {
            return ae_roi;
        }

        @Override
        public String getAE() {
            return ae_auto.toString();
        }

        @Override
        public void setAE(Long v) {
            if ((sapi != null) && sapi.inited()) {
                try {
                    sapi.enableRoi(v == 3);
                } catch (Throwable t) {
                    Log.warn("Can't set AE");
                }
            } else {
                setBasicV4l2Control(V4L2_CID_AE, v.intValue());
            }
            ae_auto = v;
        }

        @Override
        public HashMap<String, V4l2ExtControl> getMapOfControls() {
            if (mycontrolmap == null){
                mycontrolmap = MmapReadMJpeg.this.getMapOfControls();
                if ((sapi != null) && sapi.inited()) {
                    V4l2ExtControl roi = new V4l2ExtControl(0xcafebabe, 1, "isp_ae_roi", Integer.MAX_VALUE, Integer.MIN_VALUE, 1L);
                    mycontrolmap.put("isp_ae_roi",roi);
                    V4l2ExtControl ae = new V4l2ExtControl(0xabad1dea, 1, "auto_exposure", 3, 1, 1L);
                    mycontrolmap.put("auto_exposure",ae);
                }
            }
            return mycontrolmap;
        }

        @Override
        public void setOther(String name, Long v) {
            var cmap = getMapOfControls();
            var ctl = cmap.get(name);
            if (ctl != null) {
                setBasicV4l2Control(ctl.getId(), v.intValue());
            }
        }

        @Override
        public Long getOther(String name) {
            Long ret = null;
            var cmap = getMapOfControls();
            var ctl = cmap.get(name);
            if (ctl != null) {
                ret = Long.valueOf(getBasicV4l2Control(ctl.getId()));
            }
            return ret;
        }

    }
}
