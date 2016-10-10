import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.Scanner;



public class CmdLine {

  public static void execCmd(String line) throws Exception {

    Process proc = Runtime.getRuntime().exec(line);
    Reader r = new InputStreamReader(proc.getInputStream());
    BufferedReader in = new BufferedReader(r);

    String outputLine;

    while((outputLine = in.readLine()) != null)
      System.out.println(outputLine);

    in.close();
  }


  public static boolean parseCmd(String line) {
    CmdLineLexer lex = new CmdLineLexer(new ANTLRInputStream(line));
    lex.removeErrorListeners();
    lex.addErrorListener(CustomErrorListener.INSTANCE);

    CommonTokenStream tokens = new CommonTokenStream(lex);
    CmdLineParser parser = new CmdLineParser(tokens);

    ParseTree tree;

    try {
      tree = parser.script();
    } catch (Exception e) {
      System.out.println("[Err] Invalid Statement\n");
      return false;
    }

    System.out.println("valid command: " + line.toLowerCase() + "\n");

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(new CmdLineWalker(), tree);

    return true;
  }


  public static void main(String[] args) throws Exception  {

    Scanner scanner = new Scanner(System.in);

    while (true){
      String line = scanner.nextLine();

      if (line.length() == 0)
        continue;

      if(line.equals("q"))
        break;

      boolean cmdValid = parseCmd(line);

      if (cmdValid)
        execCmd(line.toLowerCase());
    }

    scanner.close();


  }

}
