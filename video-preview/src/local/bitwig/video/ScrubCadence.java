package local.bitwig.video;

/** Suppress sub-frame churn during motion; always exact-locate the final quiet target. */
final class ScrubCadence {
    private double observed=Double.NaN;
    private long changedAt;
    boolean ready(double target,double issued,double fps,long now) {
        if(Double.isNaN(observed)||Math.abs(target-observed)>0.0001) {
            observed=target;changedAt=now;
        }
        if(Double.isNaN(issued))return true;
        if(Math.abs(target-issued)<=0.0001)return false;
        // FPS is only a preview cadence hint, never a replacement for the final exact seek.
        // Thus a VFR/bogus container rate cannot change the final stopped frame.
        boolean sameFrame=Double.isFinite(fps)&&fps>0
            && Math.ceil(target*fps-0.00001)==Math.ceil(issued*fps-0.00001);
        return !sameFrame || now-changedAt>=60_000_000L;
    }
    void reset(){observed=Double.NaN;changedAt=0;}
    boolean settled(long now){return !Double.isNaN(observed)&&now-changedAt>=60_000_000L;}
}
