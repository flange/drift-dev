import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

public class CmdLineWalker extends CmdLineBaseListener {

  public CmdInfo cmdInfo = new CmdInfo();

  @Override
  public void exitQuery(CmdLineParser.QueryContext ctx) {
    cmdInfo.isQuery = true;
  }

  @Override public void exitSyncCmd(CmdLineParser.SyncCmdContext ctx) {
    cmdInfo.isAsync = false;
  }

  @Override public void exitAsyncCmd(CmdLineParser.AsyncCmdContext ctx) {
    cmdInfo.isAsync = true;
  }

  @Override public void exitService(CmdLineParser.ServiceContext ctx) {

    ServiceInfo si = new ServiceInfo();

    si.complete = "";

    for (int i = 0; i < ctx.getChildCount(); i++) {
      si.complete += ctx.getChild(i).getText();
      si.complete += " ";
    }

    si.binary = ctx.binary().ID_B().getText();

    for (CmdLineParser.ArgsContext arg : ctx.args()) {
      if (arg.ID_S() != null)
        si.args.add(arg.ID_S().getText());

      if (arg.FILE() != null)
        si.args.add(arg.FILE().getText());
    }

    cmdInfo.services.add(si);
  }

  @Override
  public void exitName(CmdLineParser.NameContext ctx) {
    cmdInfo.name = ctx.ID_S().getText();
  }



}




