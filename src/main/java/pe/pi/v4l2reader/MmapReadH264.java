/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.v4l2reader;

import com.phono.srtplight.Log;

/**
 *
 * @author thp
 */
public class MmapReadH264 extends MmapRead {
    
    public MmapReadH264(String dev, int w, int h, int rate) throws Throwable {
        super(dev, w, h, rate, V4L2_PIX_FMT_H264);
        Log.info("reading raw h264 from "+dev);
    }
    
}
