package pe.pi.v4l2reader;

/**
 *
 * @author thp
 */
public class V4l2ExtControl {

    final int id;
    final int type;
    final String name;
    final String aname;
    final long minimum;
    final long maximum;
    final long step;

    V4l2ExtControl(int id, int type, String name, long max, long min, long step) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.maximum = max;
        this.minimum = min;
        this.step = step;
        this.aname = name.toLowerCase().replaceAll(" ", "_").replaceAll(",", "");
    }

    public long getMax() {
        return maximum;
    }

    public long getMin() {
        return minimum;
    }

    public String getName() {
        return name;
    }

    public long getStep() {
        return step;
    }

    public int getId() {
        return id;
    }

    public int getType() {
        return type;
    }
    public String getAname(){
        return aname;
    }
    public String toString(){
        return name+" range "+minimum+" -> "+maximum+" step = "+step+ " type = "+type+" id = "+id;
    }
}
