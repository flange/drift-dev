import java.util.*;
import java.io.*;

public class Test {
  public static void main(String[] paramter) {

  try {

    String[] cmdScript = new String[]{"/bin/sh", "-c", "cat"};

    Process proc = Runtime.getRuntime().exec(cmdScript);

    Reader r = new InputStreamReader(proc.getInputStream());
    BufferedReader stdOut = new BufferedReader(r);


    System.out.println(proc.getInputStream().available());

//    String outLine = stdOut.readLine();



  } catch (Exception e) {
    System.out.println(e);
  }

  }
}
