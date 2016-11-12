import java.util.*;
import java.io.*;
import java.nio.file.*;

public class Test {
  public static void main(String[] paramter) {

  try {

    Path path = Paths.get("res");

    DirectoryStream<Path> realDir = Files.newDirectoryStream(path);

    for (Path entry : realDir)
      System.out.println(entry.toString());

    realDir.close();


    realDir = Files.newDirectoryStream(path);

    for (Path entry : realDir)
      System.out.println(entry.toString());


    realDir.close();




  } catch (Exception e) {
    System.out.println(e);
  }

  }
}
