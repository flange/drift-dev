import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import com.rabbitmq.client.*;



public class CmdLine {


  public static String SESSION_ID = "1234";
  public final static String TASK_QUEUE_NAME = "task";

  public final static String FS_GLOBAL  = "fs/global/";
  public static String FS_SESSION = "fs/" + SESSION_ID + "/";
  public static String FS_CWD     = FS_SESSION;

  public final static String NONAME = "anonymous";
  public final static String PROMPT = ".> ";



  public CmdInfo cmdInfo;
  public Map<String, String> nameTable = new HashMap<String, String>();
  public ServiceRegistry serviceRegistry = new ServiceRegistry();

  public void printNameTable() {
    if (nameTable.isEmpty()) {
      System.out.println("name table empty");
      return;
    }

    for (Map.Entry<String, String> entry : nameTable.entrySet())
      System.out.println(entry.getKey() + " - " + entry.getValue());

    System.out.println();

    return;
  }

  public boolean parseCmd(String line) {

    //TODO ; ??

    CmdLineLexer lex = new CmdLineLexer(new ANTLRInputStream(line));
    lex.removeErrorListeners();
    lex.addErrorListener(CustomErrorListener.INSTANCE);

    CommonTokenStream tokens = new CommonTokenStream(lex);

    CmdLineParser parser = new CmdLineParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(CustomErrorListener.INSTANCE);

    ParseTree tree;

    try {
      tree = parser.script();
    } catch (Exception e) {
      System.out.println("[Err] invalid statement\n");
      return false;
    }

    CmdLineWalker cmdWalker = new CmdLineWalker();

    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(cmdWalker, tree);

    cmdInfo = cmdWalker.cmdInfo;
    cmdInfo.sessionId = SESSION_ID;

    return true;
  }

  public void sendTask() throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);

    channel.basicPublish("", TASK_QUEUE_NAME, null, cmdInfo.toByteArray());

    channel.close();
    connection.close();
  }

  public void waitForResult(String name) {

    String queueName = name + "-" + SESSION_ID;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);
      channel.basicQos(1);

      QueueingConsumer qconsumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, qconsumer);

      QueueingConsumer.Delivery delivery = qconsumer.nextDelivery(); // blocking
      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

      cmdInfo.fromByteArray(delivery.getBody());

      channel.queueDelete(queueName);
      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void printResult(String fileName) {

    String content = "";

    try {
      content = new String(Files.readAllBytes(Paths.get(fileName)));
    } catch (Exception e) {
      System.out.println(e);
    }

    System.out.println(content);

    return;
  }

  public void handleNameQuery() {

    if (!nameTable.containsKey(cmdInfo.targetName)) {
      System.out.println("[Err] unknown name\n");
      return;
    }

    if (nameTable.get(cmdInfo.targetName).equals("")) {
      waitForResult(cmdInfo.targetName);
      nameTable.put(cmdInfo.targetName, FS_GLOBAL + cmdInfo.servicesHash());
      createFile(cmdInfo.targetName);
    }

    printResult(nameTable.get(cmdInfo.targetName));
    return;
  }

  public void handleNamespaceQuery() {

    if (!nameTable.containsKey(cmdInfo.targetNamespace)) {
      System.out.println("[Err] unknown namespace\n");
      return;
    }

    if (nameTable.get(cmdInfo.targetNamespace).equals("")) {
      waitForResult(cmdInfo.targetNamespace);
      nameTable.put(cmdInfo.targetNamespace, FS_GLOBAL + cmdInfo.servicesHash());
      createDir(cmdInfo.targetNamespace);
      cloneNamespace(cmdInfo.targetNamespace);
    }

    handleLs();
    return;
  }

  public void handleQuery() {

    if (cmdInfo.targetName != null) {
      handleNameQuery();
      return;
    }

    handleNamespaceQuery();

    return;
  }

  public void cloneNamespace(String namespace) {

/*
    System.out.println("cloneNamespace()");
    System.out.println("  namespace: " + namespace);
*/
    Path nsDir   = Paths.get(namespace);
    Path realDir = Paths.get(nameTable.get(namespace));
/*
    System.out.println("  virtDir: " + nsDir);
    System.out.println("  realDir: " + realDir);
    System.out.println();
*/
    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(realDir);

      for (Path entry : stream) {
        String virtFileName = nsDir + "/" + entry.getFileName();
        String realFileName = realDir + "/" + entry.getFileName();
/*
        System.out.println("virt File: " + virtFileName);
        System.out.println("real File: " + realFileName);
*/
        createFile(virtFileName);
        nameTable.put(virtFileName, realFileName);
      }

    } catch (IOException e) {
        e.printStackTrace();
    }

    return;
  }

  public void handleNamespaceCmd() {

    cmdInfo.print();

    // result exists without a name
    if (resultExists(cmdInfo.servicesHash())) {
      nameTable.put(cmdInfo.targetNamespace, FS_GLOBAL + cmdInfo.servicesHash());
      createDir(cmdInfo.targetNamespace);
      cloneNamespace(cmdInfo.targetNamespace);
      return;
    }

    // no result
    nameTable.put(cmdInfo.targetNamespace, "");

    try {
      sendTask();
    } catch (Exception e) {
      System.out.println(e);
    }

    if (!cmdInfo.isAsync) {
      waitForResult(cmdInfo.targetNamespace);
      nameTable.put(cmdInfo.targetNamespace, FS_GLOBAL + cmdInfo.servicesHash() + "/");
      createDir(cmdInfo.targetNamespace);
      cloneNamespace(cmdInfo.targetNamespace);
    }

  }

  public void handleNameCmd() {

    // anonymous task
    if ((cmdInfo.targetName == null) && (cmdInfo.targetNamespace == null)) {

      if (resultExists(cmdInfo.servicesHash())) {
        printResult(FS_GLOBAL + cmdInfo.servicesHash());
        return;
      }

      try {
        sendTask();
      } catch (Exception e) {
        System.out.println(e);
      }

      waitForResult(NONAME);
      printResult(FS_GLOBAL + cmdInfo.servicesHash());

      return;
    }

    // result exists without a name
    if (resultExists(cmdInfo.servicesHash())) {
      nameTable.put(cmdInfo.targetName, FS_GLOBAL + cmdInfo.servicesHash());
      createFile(cmdInfo.targetName);
      return;
    }

    // no result
    nameTable.put(cmdInfo.targetName, "");

    try {
      sendTask();
    } catch (Exception e) {
      System.out.println(e);
    }

    if (!cmdInfo.isAsync) {
      waitForResult(cmdInfo.targetName);
      nameTable.put(cmdInfo.targetName, FS_GLOBAL + cmdInfo.servicesHash());
      createFile(cmdInfo.targetName);
    }

    return;
  }

  public void handleCmd() {

    if (cmdInfo.resultIsNamespace) {
      handleNamespaceCmd();
      return;
    }

    handleNameCmd();
    return;
  }

  public boolean isNamespace(String name) {
    return (name.substring(name.length() - 1)).equals("/");
  }

  public boolean checkCmd() {

    for (ServiceInfo si : cmdInfo.services) {
      if (!serviceRegistry.isValid(si)) {
        System.out.println("[Err] invalid service '" +  si.binary +"'\n");
        return false;
      }

      for (String name : si.argNames) {
        String sessionName = FS_CWD + name;

        if (!nameTable.containsKey(sessionName)) {
          System.out.println("[Err] unknown name '" + name + "'\n");
          return false;
        }

        String globalName = nameTable.get(sessionName);
        si.argNames.set(si.argNames.indexOf(name), globalName);
      }
    }

    if (cmdInfo.targetName != null)
      cmdInfo.targetName = FS_CWD + cmdInfo.targetName;

    if (cmdInfo.targetNamespace != null)
      cmdInfo.targetNamespace = FS_CWD + cmdInfo.targetNamespace;

    for (ServiceInfo si : cmdInfo.services)
      si.toString();

    return true;
  }


  public boolean resultExists(String resName) {
    return Files.exists(Paths.get(FS_GLOBAL + resName));
  }

  public void createFile(String path) {
    try {
      if (Files.notExists(Paths.get(path)))
        Files.createFile(Paths.get(path));
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public static void createDir(String path) {

    try {
      Path dir = Paths.get(path);

      if (Files.notExists(dir))
        Files.createDirectory(dir);
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public static void importFile(String filePath, CmdLine cmdLine) {

    Path path = Paths.get(filePath);
    Path pathGlobal = Paths.get(FS_GLOBAL + filePath);
    Path pathLocal = Paths.get(FS_SESSION + filePath);

    try {
      if (Files.notExists(pathGlobal))
        Files.copy(path, pathGlobal);

      if (Files.notExists(pathLocal))
        Files.createFile(pathLocal);

    } catch (Exception e) {
      System.out.println(e);
    }
    cmdLine.nameTable.put(pathLocal.toString(), pathGlobal.toString());

  }

  public static void importDir(String dirPath, CmdLine cmdLine) {

    Path path = Paths.get(dirPath);
    Path pathGlobal = Paths.get(FS_GLOBAL + dirPath);
    Path pathLocal = Paths.get(FS_SESSION + dirPath);

    try {
      if (Files.notExists(pathGlobal))
        Files.createDirectory(pathGlobal);

      if (Files.notExists(pathLocal))
        Files.createDirectory(pathLocal);

      cmdLine.nameTable.put(pathLocal.toString() + "/", pathGlobal.toString() + "/");

      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      for (Path entry : stream)
        importFile(entry.toString(), cmdLine);

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public static void importSetup(CmdLine cmdLine) {

    importFile("my.tar", cmdLine);
    importFile("test.txt", cmdLine);
    importFile("foo.txt", cmdLine);

    //importDir("res/", cmdLine);


    return;
  }

  public static void initServiceRegistry(CmdLine cmdLine) {

    ServiceWrapper untar = new ServiceWrapper("Untar", "tar -xf ", false, true);
    cmdLine.serviceRegistry.db.put("Untar", untar);

    ServiceWrapper cat = new ServiceWrapper("Cat", "cat  ", false, false);
    cmdLine.serviceRegistry.db.put("Cat", cat);
  }

  public void handleLs() {

    if (cmdInfo.targetNamespace != null) {

      if (!Files.isDirectory(Paths.get(cmdInfo.targetNamespace))) {
        System.out.println("[Err] not a namespace\n");
        return;
      }

      ls(Paths.get(cmdInfo.targetNamespace));
      return;
    }

    ls(Paths.get(FS_CWD));
    return;
  }

  public static void ls(Path path) {

    if (!Files.isDirectory(path)) {
      System.out.println(path.getFileName());
      return;
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      if (!stream.iterator().hasNext()) {
        System.out.println("(empty)");
        return;
      }

    } catch (IOException e) {
        e.printStackTrace();
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      for (Path entry : stream) {

        if (!Files.isDirectory(entry))
          continue;

        System.out.println("  \033[0;1m" + (char)27 + "[34m" + entry.getFileName() + "/" + (char)27 + "[37m" + "\u001B[0m");
      }
    } catch (IOException e) {
        e.printStackTrace();
    }

    try {
      DirectoryStream<Path> stream = Files.newDirectoryStream(path);

      for (Path entry : stream) {

        if (Files.isDirectory(entry))
          continue;

        System.out.println("  " + entry.getFileName());
        //System.out.println("\033[0;1m" + entry.getFileName() + "\u001B[0m");
      }
    } catch (IOException e) {
        e.printStackTrace();
    }

    System.out.println();

    return;
  }

  public void handleRm() {

    if (cmdInfo.targetName != null) {

      if (!nameTable.containsKey(cmdInfo.targetName)) {
        System.out.println("[Err] unknown name");
        return;
      }

      nameTable.remove(cmdInfo.targetName);

      try {
        Files.delete(Paths.get(cmdInfo.targetName));
      } catch (Exception e) {
        System.out.println(e);
      }

      return;
    }

    try {
      Files.walkFileTree(Paths.get(cmdInfo.targetNamespace), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          nameTable.remove(file.toString());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          nameTable.remove(dir.toString() + "/");
          return FileVisitResult.CONTINUE;
        }
      });
    } catch(Exception e){
      e.printStackTrace();
    }

    return;
  }

  public void handleCd() {

    if (cmdInfo.targetNamespace == null) {

      if (FS_CWD.equals(FS_SESSION)) {
        System.out.println("(root namespace)");
        return;
      }

      Path cwd = Paths.get(FS_CWD);
      Path res = Paths.get(FS_CWD);

      for (int i = 0; i < cmdInfo.upCnt; i++) {
        res = cwd.getParent();

        if (res.toString().equals(FS_SESSION))
          break;
      }

      cd(res);
      return;
    }

    if (!Files.isDirectory(Paths.get(cmdInfo.targetNamespace))) {
      System.out.println("[Err] not a namespace\n");
      return;
    }

    cd(Paths.get(cmdInfo.targetNamespace));

    return;
  }

  public void cd(Path path) {

    FS_CWD = path.toString() + "/";
    System.out.println();
    return;
  }

  public static void main(String[] args) throws Exception  {

    if (args.length > 0) {
      SESSION_ID = args[0];
      FS_SESSION = "fs/" + SESSION_ID + "/";
      FS_CWD = FS_SESSION;
    }

    System.out.println("Session: " + SESSION_ID);

    CmdLine cmdLine = new CmdLine();
    Scanner scanner = new Scanner(System.in);

    createDir("fs");
    createDir("fs/global");
    createDir("fs/" + SESSION_ID);

    importSetup(cmdLine);
    initServiceRegistry(cmdLine);

    while (true) {
      if (FS_CWD.equals(FS_SESSION))
            System.out.print("/ " + PROMPT);
      else
        System.out.print(Paths.get(FS_CWD).getFileName()  + "/ " + PROMPT);


      String line = scanner.nextLine();

      if (line.length() == 0)
        continue;

      if(line.equals("q")) {
        Runtime.getRuntime().exec("rm -rf " + FS_SESSION);
        break;
      }

      if (line.equals("r")) {
        cmdLine.printNameTable();
        continue;
      }

      if (line.equals("cl")) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println("Session: " + SESSION_ID);
        continue;
      }

      if (line.equals("cwd")) {
        System.out.println(FS_CWD + "\n");
        continue;
      }

      boolean parseCorrect = cmdLine.parseCmd(line);
      if (!parseCorrect)
        continue;

      boolean semanticCorrect = cmdLine.checkCmd();
      if (!semanticCorrect)
        continue;

      //cmdLine.cmdInfo.print();


      if (cmdLine.cmdInfo.isQuery) {
        cmdLine.handleQuery();
        continue;
      }

      if (cmdLine.cmdInfo.isLs) {
        cmdLine.handleLs();
        continue;
      }

      if (cmdLine.cmdInfo.isCd) {
        cmdLine.handleCd();
        continue;
      }

      if (cmdLine.cmdInfo.isRm) {
        cmdLine.handleRm();
        continue;
      }


      cmdLine.handleCmd();
    }

    scanner.close();

    return;
  }


}
