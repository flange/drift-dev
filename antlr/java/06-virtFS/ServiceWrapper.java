import java.util.*;

public class ServiceWrapper {

  public String name;
  public String exec;
  public boolean nsIn;
  public boolean nsOut;

  public ServiceWrapper(String name, String exec, boolean nsIn, boolean nsOut) {
    this.name = name;
    this.exec = exec;
    this.nsIn = nsIn;
    this.nsOut = nsOut;
  }

}
