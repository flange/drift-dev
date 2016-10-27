import java.util.*;
import java.io.*;

public class ServiceInfo implements Serializable {

  public  String complete = null;
  public  String binary = null;
  public boolean nsIn = false;
  public boolean nsOut = false;

  public  ArrayList<String> args = new ArrayList<String>();
  public  ArrayList<String> argNames = new ArrayList<String>();

  public void print() {

    System.out.println("    ServiceInfo:");
    System.out.println("      complete: " + complete);
    System.out.println("      binary:   " + binary);
    System.out.print("      args:     ");

    for (String arg : args)
      System.out.print(arg + " ");

    System.out.println();

    System.out.print("      names:    ");

    for (String name : argNames)
      System.out.print(name + " ");

    System.out.println();

    System.out.println("      nsIn:     " + nsIn);
    System.out.println("      nsOut:    " + nsOut);

  }

  public boolean resultIsNamespace() {
    return nsOut;
  }

  public String toString() {

    complete = binary.toLowerCase();

    for (String arg : args)
      complete += " " + arg;

    for (String argName : argNames)
      complete += " " + argName;

    return complete;
  }

}
