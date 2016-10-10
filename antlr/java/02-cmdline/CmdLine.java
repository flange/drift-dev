import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.Scanner;



public class CmdLine {

  public static void parseCmd(String line) {
    CmdLineLexer lex = new CmdLineLexer(new ANTLRInputStream(line));
    CommonTokenStream tokens = new CommonTokenStream(lex);
    CmdLineParser parser = new CmdLineParser(tokens);

    ParseTree tree = parser.script();
    ParseTreeWalker walker = new ParseTreeWalker();

    walker.walk(new CmdLineWalker(), tree);

    return;
  }

  public static void main(String[] args) throws Exception  {

    Scanner scanner = new Scanner(System.in);

    while (true){
      String line = scanner.nextLine();

      if(line.equals("q"))
        break;

      parseCmd(line);
    }

    scanner.close();


  }

}
