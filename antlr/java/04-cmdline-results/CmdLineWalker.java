import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

public class CmdLineWalker extends CmdLineBaseListener {

  public static String name     = null;
  public static String binary   = null;
  public static ArrayList<String> args = new ArrayList<String>();

  public static ArrayList<String> services = new ArrayList<String>();


  public static void printInfo() {

    System.out.println();
    System.out.println("name:    " + name);
    System.out.println("service: " + String.join(" ", services));
    System.out.println("  bin:  " + binary);

    for (String arg : args) {
      System.out.println("  arg: " + arg);
    }

    System.out.println();
  }

  public static void clear() {
    args.clear();
    services.clear();
  }


  @Override public void exitService(CmdLineParser.ServiceContext ctx) {

    String s = "";

    for (int i = 0; i < ctx.getChildCount(); i++) {
      s += ctx.getChild(i).getText();
      s += " ";
    }

    services.add(s);

  }

  @Override public void exitCmd(CmdLineParser.CmdContext ctx) {

    name = ctx.name().ID_S().getText();
    binary = ctx.service().binary().ID_B().getText();

    for (CmdLineParser.ArgsContext arg : ctx.service().args()) {
      if (arg.ID_S() != null)
        args.add(arg.ID_S().getText());

      if (arg.FILE() != null)
        args.add(arg.FILE().getText());
    }

    return;
  }

}
