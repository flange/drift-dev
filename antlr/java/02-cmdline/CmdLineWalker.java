import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;


public class CmdLineWalker extends CmdLineBaseListener {

  @Override public void exitCmd(CmdLineParser.CmdContext ctx) {
    System.out.println();
    System.out.println("Command:");

    System.out.println(" bin: " + ctx.Binary().getText());

    for (TerminalNode arg : ctx.Args()) {
      System.out.println(" arg: " + arg.getText());
    }

    System.out.println();
  }

}
