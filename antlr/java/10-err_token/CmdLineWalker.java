import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.*;

public class CmdLineWalker extends CmdLineBaseListener {

  public CmdInfo cmdInfo = new CmdInfo();

  public String reconstructName(List<TerminalNode> tlist) {

    ArrayList<String> l = new ArrayList<String>();

    for (TerminalNode t : tlist)
      l.add(t.getText());

    return String.join("/", l);
  }

  public String reconstructNamespace(List<TerminalNode> tlist) {

    ArrayList<String> l = new ArrayList<String>();

    for (TerminalNode t : tlist)
      l.add(t.getText());

    return String.join("/", l) + "/";
  }


  @Override public void exitCd(CmdLineParser.CdContext ctx) {
    cmdInfo.isCd = true;
  }

  @Override public void exitLs(CmdLineParser.LsContext ctx) {
    cmdInfo.isLs = true;
  }

  @Override public void exitRm(CmdLineParser.RmContext ctx) {
    cmdInfo.isRm = true;
  }

  @Override public void exitQuery(CmdLineParser.QueryContext ctx) {
    cmdInfo.isQuery = true;
  }

  @Override public void exitCmd(CmdLineParser.CmdContext ctx) {

    ServiceInfo lastService = cmdInfo.services.get(cmdInfo.services.size() - 1);
    cmdInfo.resultIsNamespace = lastService.resultIsNamespace();
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

    for (int i = 0; i < ctx.getChildCount(); i++)
      si.complete += " " + ctx.getChild(i).getText();

    si.binary = ctx.binary().ID_B().getText();

    for (CmdLineParser.ArgsContext arg : ctx.args())
        si.args.add(arg.OPTION().getText());

    for (CmdLineParser.ArgNameContext argName : ctx.argName()) {

      if (argName.name() != null) {
        si.argNames.add(reconstructName(argName.name().ID_S()));
        continue;
      }

      if (argName.namespace() != null) {
        si.argNames.add(reconstructNamespace(argName.namespace().ID_S()));
        continue;
      }

    }

    cmdInfo.services.add(si);
  }

  @Override public void exitServiceNsOut(CmdLineParser.ServiceNsOutContext ctx) {

    ServiceInfo si = new ServiceInfo();

    si.complete = "";

    for (int i = 0; i < ctx.getChildCount(); i++) {
      String child = ctx.getChild(i).getText();

      if (child.equals("*"))
        continue;

      si.complete += child + " ";
    }

    si.binary = ctx.binary().ID_B().getText();

    for (CmdLineParser.ArgNameContext argName : ctx.argName()) {

      if (argName.name() != null) {
        si.argNames.add(reconstructName(argName.name().ID_S()));
        continue;
      }

      if (argName.namespace() != null) {
        si.argNames.add(reconstructNamespace(argName.namespace().ID_S()));
        continue;
      }

    }

    si.nsOut = true;

    cmdInfo.services.add(si);

  }

  @Override public void exitServiceNsIn(CmdLineParser.ServiceNsInContext ctx) {

      ServiceInfo si = new ServiceInfo();

    si.complete = "";

    for (int i = 0; i < ctx.getChildCount(); i++) {
      si.complete += ctx.getChild(i).getText();
      si.complete += " ";
    }

    si.binary = ctx.binary().ID_B().getText();

    for (CmdLineParser.ArgNameContext argName : ctx.argName()) {

      if (argName.name() != null) {
        si.argNames.add(reconstructName(argName.name().ID_S()));
        continue;
      }

      if (argName.namespace() != null) {
        si.argNames.add(reconstructNamespace(argName.namespace().ID_S()));
        continue;
      }

    }

    si.nsIn = true;

    cmdInfo.services.add(si);
  }

  @Override public void exitServiceNsInOut(CmdLineParser.ServiceNsInOutContext ctx) {

    ServiceInfo si = new ServiceInfo();

    si.complete = "";

    for (int i = 0; i < ctx.getChildCount(); i++) {
      si.complete += ctx.getChild(i).getText();
      si.complete += " ";
    }

    si.binary = ctx.binary().ID_B().getText();

    for (CmdLineParser.ArgNameContext argName : ctx.argName()) {

      if (argName.name() != null) {
        si.argNames.add(reconstructName(argName.name().ID_S()));
        continue;
      }

      if (argName.namespace() != null) {
        si.argNames.add(reconstructNamespace(argName.namespace().ID_S()));
        continue;
      }

    }

    si.nsIn = true;
    si.nsOut = true;

    cmdInfo.services.add(si);
  }

  @Override public void exitTargetName(CmdLineParser.TargetNameContext ctx) {

    ArrayList<String> l = new ArrayList<String>();

    for (TerminalNode t : ctx.name().ID_S())
      l.add(t.getText());

    cmdInfo.targetName = String.join("/", l);

  }

  @Override public void exitTargetNamespace(CmdLineParser.TargetNamespaceContext ctx) {

    String ns = "";

    for (TerminalNode s : ctx.namespace().ID_S()) {
      if (s != null)
        ns += s + "/";
    }

    cmdInfo.targetNamespace = ns;
  }

  @Override public void exitNamespace(CmdLineParser.NamespaceContext ctx) {

    cmdInfo.targetNamespace = reconstructNamespace(ctx.ID_S());

  }

  @Override public void exitUp(CmdLineParser.UpContext ctx) {

    cmdInfo.upCnt = ctx.UP().size();

  }

}




