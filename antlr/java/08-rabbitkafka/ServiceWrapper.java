import java.util.*;

public abstract class ServiceWrapper {

  public String name;
  public boolean nsIn;
  public boolean nsOut;

  public ServiceWrapper(boolean nsIn, boolean nsOut) {
    this.nsIn = nsIn;
    this.nsOut = nsOut;
  }

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

  }
}
