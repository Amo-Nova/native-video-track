package local.bitwig.video;
public final class TransportTimeTest {
    public static void main(String[] args){
        TransportTime t=new TransportTime();Object a=new Object(),b=new Object();
        eq(t.update(a,0,0,false),0);eq(t.update(a,8,0,true),8);
        eq(t.update(a,8.02,0,false),8.02); // pause holds current frame
        eq(t.update(a,8.02,3,false),3); // blue marker moved while stopped
        eq(t.update(a,8.02,3,false),3); // old stopped transport must not override it
        eq(t.update(a,3.1,3,true),3.1);eq(t.update(a,5.9,3,true),5.9);
        eq(t.update(a,3.0,3,true),3); // loop back is an actual clock jump
        eq(t.update(b,0,0,false),0); // new project does not inherit old cursor
        eq(t.update(b,11,11,false),11);
        System.out.println("TRANSPORT_TIME_OK pause, locate, loop, project switch");
    }
    static void eq(double a,double b){if(Math.abs(a-b)>0.000001)throw new AssertionError(a+" != "+b);}
}
