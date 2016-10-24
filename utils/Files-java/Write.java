import java.nio.file.*;
import java.nio.charset.*;

import java.io.*;
import java.util.*;

public class Write {
  public static void main(String[] args) throws Exception {

    String res = "Result: abc123 fo";

    List<String> lines = Arrays.asList(res);

    Path file = Paths.get("result.txt");
    Files.write(file, lines, Charset.forName("UTF-8"));
    //Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
  }

}
