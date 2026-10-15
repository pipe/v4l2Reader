/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import com.phono.srtplight.Log;
import java.util.HashMap;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_AE;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_BRIGHTNESS;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_CONTRAST;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_EXPOSURE;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_HUE;
import static pe.pi.v4l2reader.V4l2Structs.V4L2_CID_SATURATION;

/**
 *
 * @author thp
 */
public class MmapV4l2Substitute implements V4l2Substitute, ControlMapper {

    HashMap<String, V4l2ExtControl> mycontrolmap;
    private final MmapRead mm;

    MmapV4l2Substitute(MmapRead mm) {
        this.mm = mm;
    }

    @Override
    public void setBrightness(Long v) {
        mm.setBasicV4l2Control(V4L2_CID_BRIGHTNESS, v.intValue());
    }

    @Override
    public void setHue(Long v) {
        mm.setBasicV4l2Control(V4L2_CID_HUE, v.intValue());
    }

    @Override
    public void setContrast(Long v) {
        mm.setBasicV4l2Control(V4L2_CID_CONTRAST, v.intValue());
    }

    @Override
    public void setExposure(Long v) {
        mm.setBasicV4l2Control(V4L2_CID_EXPOSURE, v.intValue());
    }

    @Override
    public Long getBrightness() {
        return Long.valueOf(mm.getBasicV4l2Control(V4L2_CID_BRIGHTNESS));
    }

    @Override
    public Long getHue() {
        return Long.valueOf(mm.getBasicV4l2Control(V4L2_CID_HUE));
    }

    @Override
    public Long getContrast() {
        return Long.valueOf(mm.getBasicV4l2Control(V4L2_CID_CONTRAST));
    }

    @Override
    public Long getExposure() {
        return Long.valueOf(mm.getBasicV4l2Control(V4L2_CID_EXPOSURE));
    }

    @Override
    public void setSaturation(Long v) {
        mm.setBasicV4l2Control(V4L2_CID_SATURATION, v.intValue());
    }

    @Override
    public Long getSaturation() {
        return Long.valueOf(mm.getBasicV4l2Control(V4L2_CID_EXPOSURE));
    }

    @Override
    public String getSensorName() {
        return "";
    }

    // these don't work at the moment so we parrot - unless we have a working sonixAPI.
    @Override
    public void setAERoi(Long v) {
    }

    @Override
    public Long getAERoi() {
        return 0L;
    }

    @Override
    public String getAE() {
        return "" + mm.getBasicV4l2Control(V4L2_CID_AE);

    }

    @Override
    public void setAE(Long v) {

        mm.setBasicV4l2Control(V4L2_CID_AE, v.intValue());

    }

    @Override
    public HashMap<String, V4l2ExtControl> getMapOfControls() {
        if (mycontrolmap == null) {
            mycontrolmap = mm.getMapOfControls();
        }
        return mycontrolmap;
    }

    @Override
    public void setOther(String name, Long v) {
        var cmap = getMapOfControls();
        var ctl = cmap.get(name);
        if (ctl != null) {
            mm.setBasicV4l2Control(ctl.getId(), v.intValue());
        }
    }

    @Override
    public Long getOther(String name) {
        Long ret = null;
        var cmap = getMapOfControls();
        var ctl = cmap.get(name);
        if (ctl != null) {
            ret = Long.valueOf(mm.getBasicV4l2Control(ctl.getId()));
        }
        return ret;
    }

}
