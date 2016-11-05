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

  public void sendTask(ArrayList<CmdInfo> cmds) {

    ArrayList<String> queues = getQueues();
    ArrayList<CmdInfo> sendList = new ArrayList<CmdInfo>();

    System.out.println("sendTask():");
    System.out.println("queues:");
    for (String q : queues)
      System.out.println("  " + q);

    System.out.println();


    System.out.println("cmds result Qs:");
    for (CmdInfo cmd : cmds)
      System.out.println(FS_GLOBAL + cmd.servicesHash());


    ListIterator<CmdInfo> cmdsIt = cmds.listIterator(cmds.size());

    while(cmdsIt.hasPrevious()) {
      CmdInfo prev = cmdsIt.previous();

      String cmdResultQueue = FS_GLOBAL + prev.servicesHash();

      if (queues.contains(cmdResultQueue))
        break;

      System.out.println("need to send task with result Q: " + cmdResultQueue);
      sendList.add(prev);
    }

    if (sendList.size() == 0)
      return;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.confirmSelect();

      channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);


      cmdsIt = sendList.listIterator(sendList.size());

      while(cmdsIt.hasPrevious()) {
        CmdInfo prev = cmdsIt.previous();
        System.out.println("sending task with resQ: " + FS_GLOBAL + prev.servicesHash());

        channel.basicPublish("", TASK_QUEUE_NAME, null, prev.toByteArray());
        channel.waitForConfirms();
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
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

  public void printResult(String queueName) {

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, consumer);

      while (true) {
        QueueingConsumer.Delivery delivery = consumer.nextDelivery();

        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        System.out.println(new String(delivery.getBody()));
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void handleNameQuery() {

    if (!nameTable.containsKey(cmdInfo.targetName)) {
      System.out.println("[Err] unknown name\n");
      return;
    }

    printResult(nameTable.get(cmdInfo.targetName));
    return;
  }

  public void handleNamespaceQuery() {

    if (!nameTable.containsKey(cmdInfo.targetNamespace)) {
      System.out.println("[Err] unknown namespace\n");
      return;
    }

    printResult(nameTable.get(cmdInfo.targetNamespace));
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

    String dirName = Paths.get(namespace).getFileName().toString() + "/";

    nameTable.put(FS_SESSION + dirName, namespace);
    createDir(FS_SESSION + dirName);

    String queueName = namespace;

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        String name = new String(delivery.getBody());
        nameTable.put(FS_SESSION + dirName + name, FS_GLOBAL + dirName + name);

        createFile(FS_SESSION + dirName + name);
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void receiveNamespace(String queueName, String namespace) {

    nameTable.put(namespace, queueName);
    createDir(namespace);

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(queueName, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(queueName, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        String name = new String(delivery.getBody());
        //System.out.println("receiveNS() put(" + namespace + name + ", " + queueName + name + ")");

        nameTable.put(namespace + name, queueName + name);

        createFile(namespace + name);
      }

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public ArrayList<CmdInfo> splitCmd() {

    ArrayList<CmdInfo> cmds = new ArrayList<CmdInfo>();

    if (cmdInfo.services.size() == 1) {
      cmds.add(cmdInfo);
      return cmds;
    }

    ServiceInfo firstSi = cmdInfo.services.get(0);
    CmdInfo first = new CmdInfo(cmdInfo);
    first.services = new ArrayList<ServiceInfo>();
    first.services.add(firstSi);

    cmds.add(first);

    String lastHash = first.servicesHash();

    for (int i = 1; i < cmdInfo.services.size(); i++) {

      ServiceInfo si = cmdInfo.services.get(i);

      if (si.argNames.isEmpty())
        si.argNames.add(FS_GLOBAL + lastHash);
      else
        si.argNames.set(0, FS_GLOBAL + lastHash);

      CmdInfo ci = new CmdInfo(cmdInfo);
      ci.services = new ArrayList<ServiceInfo>();
      ci.services.add(si);

      cmds.add(ci);

      lastHash = ci.servicesHash();
    }

    return cmds;
  }

  public void handleNamespaceCmd() {

    ArrayList<CmdInfo> cmds = splitCmd();
    CmdInfo lastCmd = cmds.get(cmds.size() - 1);

    // result exists
    if (resultExists(cmdInfo.servicesHash() + "/")) {
      //System.out.println("handleNamespaceCmd(): result exists");
      nameTable.put(cmdInfo.targetNamespace, FS_GLOBAL + cmdInfo.servicesHash());
      receiveNamespace(FS_GLOBAL + cmdInfo.servicesHash() + "/", cmdInfo.targetNamespace);
      return;
    }

    // no result
    nameTable.put(cmdInfo.targetNamespace, FS_GLOBAL + cmdInfo.servicesHash() + "/");
    sendTask(cmds);

    if (!cmdInfo.isAsync)
      receiveNamespace(FS_GLOBAL + cmdInfo.servicesHash() + "/", cmdInfo.targetNamespace);

    return;
  }

  public void handleNameCmd() {

    ArrayList<CmdInfo> cmds = splitCmd();
    CmdInfo lastCmd = cmds.get(cmds.size() - 1);

    // anonymous task
    if ((cmdInfo.targetName == null) && (cmdInfo.targetNamespace == null)) {

      sendTask(cmds);
      printResult(FS_GLOBAL + lastCmd.servicesHash());

      return;
    }

    createFile(cmdInfo.targetName);

    // result exists
    if (resultExists(cmdInfo.servicesHash())) {
      nameTable.put(cmdInfo.targetName, FS_GLOBAL + cmdInfo.servicesHash());
      return;
    }

    // no result
    nameTable.put(cmdInfo.targetName, FS_GLOBAL + cmdInfo.servicesHash());
    sendTask(cmds);

    if (!cmdInfo.isAsync)
      printResult(FS_GLOBAL + cmdInfo.servicesHash());

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
    return hasQueue(FS_GLOBAL + resName);
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

  public ArrayList<String> getQueues() {

    ArrayList<String> queues = new ArrayList<String>();

    String[] cmdScript = new String[]{"/bin/sh", "-c", "sudo rabbitmqctl list_queues | tail -n +2 | awk '{print $1}'"};

    try {
      Process proc = Runtime.getRuntime().exec(cmdScript);
      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader in = new BufferedReader(r);

      String outputLine;

      while((outputLine = in.readLine()) != null)
        queues.add(outputLine);

      in.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return queues;
  }

  public boolean hasQueue(String queueName) {

    String[] cmdScript = new String[]{"/bin/sh", "-c", "sudo rabbitmqctl list_queues | tail -n +2 | awk '{print $1}'"};

    try {
      Process proc = Runtime.getRuntime().exec(cmdScript);
      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader in = new BufferedReader(r);

      String outputLine;

      while((outputLine = in.readLine()) != null) {
        if (outputLine.equals(queueName)) {
          in.close();
          return true;
        }
      }

      in.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return false;
  }

  public void sendFileToQueue(String filePath) {

    Path pathLocal = Paths.get(filePath);
    Path queueName = Paths.get(FS_GLOBAL + filePath);

    String fileContent = "";

    try {
      //fileContent = new String(Files.readAllBytes(pathLocal));

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();

      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName.toString(), false, false, false, null);


      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        Files.readAllBytes(pathLocal));

      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

      channel.close();
      connection.close();

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
      if (!cmdLine.hasQueue(pathGlobal.toString())) {
        cmdLine.sendFileToQueue(filePath);
      }

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

    String pathLocalStr = pathLocal.toString() + "/";
    String pathGlobalStr = pathGlobal.toString() + "/";

    try {
      if (cmdLine.hasQueue(pathGlobalStr)) {
        cmdLine.cloneNamespace(pathGlobalStr);
        return;
      }

      if (Files.notExists(pathLocal))
        Files.createDirectory(pathLocal);

      cmdLine.nameTable.put(pathLocalStr, pathGlobalStr);

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(pathGlobalStr, false, false, false, null);

      DirectoryStream<Path> realDir = Files.newDirectoryStream(path);

      for (Path entry : realDir) {
        importFile(entry.toString(), cmdLine);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("eof",  false);

        channel.basicPublish("", pathGlobalStr, new AMQP.BasicProperties.Builder()
          .headers(headers)
          .build(),
          entry.getFileName().toString().getBytes());

      }

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", pathGlobalStr, new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public static void importSetup(CmdLine cmdLine) {

    importFile("my.tar", cmdLine);
    importFile("test.txt", cmdLine);
    importFile("foo.txt", cmdLine);
    importFile("nums.txt", cmdLine);

    //importDir("res/", cmdLine);

    return;
  }

  public static void initServiceRegistry(CmdLine cmdLine) {

    ServiceWrapper cat = new Cat(false, false);
    cmdLine.serviceRegistry.db.put(cat.name, cat);

    ServiceWrapper square = new Square(false, false);
    cmdLine.serviceRegistry.db.put(square.name, square);

    ServiceWrapper add = new Add(false, false);
    cmdLine.serviceRegistry.db.put(add.name, add);

    ServiceWrapper emitter = new Emitter(false, false);
    cmdLine.serviceRegistry.db.put(emitter.name, emitter);

    ServiceWrapper emitter2 = new Emitter2(false, false);
    cmdLine.serviceRegistry.db.put(emitter2.name, emitter2);

    ServiceWrapper untar = new Untar(false, true);
    cmdLine.serviceRegistry.db.put(untar.name, untar);


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

        //http://stackoverflow.com/questions/1448858/how-to-color-system-out-println-output
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

        if (entry.toString().endsWith(".tar")) {
          System.out.println("  " + (char)27 + "[31m" + entry.getFileName() + (char)27 + "[37m");
          continue;
        }

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

      cmdLine.cmdInfo.print();


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
