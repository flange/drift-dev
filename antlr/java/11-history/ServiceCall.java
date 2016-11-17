import java.util.*;

public class ServiceCall {


  public String service = "-";
  public ArrayList<NameLogEntry> args = new ArrayList<NameLogEntry>();

  public String toString() {
    String res = service;

    for (NameLogEntry a : args)
      res += " " + a.name;

    return res;
  }
}
