package pe.pi.v4l2reader;


/**
 *
 * @author thp
 */
public interface V4l2Substitute {

    public void setBrightness(Long v);

    public void setHue(Long v);

    public void setContrast(Long v);

    public void setExposure(Long v);

    public Long getBrightness();

    public Long getHue();

    public Long getContrast();

    public Long getExposure();

    public void setSaturation(Long v);

    public Long getSaturation();

    public void setAERoi(Long v);

    public Long getAERoi();

    public String getSensorName();

    public String getAE();

    public void setAE(Long v);
    
    public void setOther(String name,Long v);
    public Long getOther(String name);
    

}
