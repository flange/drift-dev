import java.io.*;

public class Exec {

  public static void main(String[] args) throws Exception {

    String cmd = "ls -l";

    Process proc = Runtime.getRuntime().exec(cmd);
    Reader r = new InputStreamReader(proc.getInputStream());
    BufferedReader in = new BufferedReader(r);

    String outputLine;

    while((outputLine = in.readLine()) != null)
      System.out.println(outputLine);

    in.close();

  }

}
