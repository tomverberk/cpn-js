package com.indevstudio.cpnide.server.net;

import com.indevstudio.cpnide.server.createLog.CreateLogConfig;
import com.indevstudio.cpnide.server.createLog.CreateLogContainer;
import com.indevstudio.cpnide.server.model.*;
import com.indevstudio.cpnide.server.model.monitors.MonitorTemplate;
import com.indevstudio.cpnide.server.model.monitors.MonitorTemplateFactory;
import com.indevstudio.cpnide.server.model.monitors.MonitorTypes;
import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.cpntools.accesscpn.engine.SimulatorService;
import org.cpntools.accesscpn.engine.highlevel.*;
import org.cpntools.accesscpn.engine.highlevel.checker.Checker;
import org.cpntools.accesscpn.engine.highlevel.instance.Binding;
import org.cpntools.accesscpn.engine.highlevel.instance.Instance;
import org.cpntools.accesscpn.engine.highlevel.instance.Marking;
import org.cpntools.accesscpn.engine.highlevel.instance.ValueAssignment;
import org.cpntools.accesscpn.model.*;
import org.cpntools.accesscpn.model.exporter.DOMGenerator;
import org.cpntools.accesscpn.model.importer.DOMParser;
import org.cpntools.accesscpn.model.monitors.Monitor;
import org.deckfour.xes.model.XLog;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.w3c.dom.Document;

import javax.annotation.PostConstruct;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.Object;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PetriNetContainer {

    private ConcurrentHashMap<String, PetriNet> usersNets = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Checker> usersCheckers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, HighLevelSimulator> usersSimulator = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, NetInfo> netInf = new ConcurrentHashMap<>();
    CreateLogContainer createLogContainer;
    TokenController tokenController;
    HighLevelSimulator _sim;
    private final static Object lock = new Object();

    private static final String OUTPUT_MODEL_PATH = "model_out";

    @PostConstruct
    void Init() throws Exception {
        // _sim =
        // HighLevelSimulator.getHighLevelSimulator(SimulatorService.getInstance().getNewSimulator());
    }

    public void CreateNewNet(String sessionId, String xml, boolean restartSim) throws Exception {
        log.debug("Before Lock: CreateNewNet");
        synchronized (lock) {
            log.debug("After Lock: CreateNewNet");
            PetriNet net = DOMParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), sessionId);


            // String fileName = "/home/semenov-k/Downloads/fuelstation_4c.cpn";
            // File file1 = new File(fileName);
            // FileInputStream in = new FileInputStream(file1);
            // PetriNet net = org.cpntools.accesscpn.model.importer.DOMParser.parse(in,
            // sessionId);
            usersNets.put(sessionId, net);
            // HighLevelSimulator sim = usersSimulator.get(sessionId);
            // if (sim == null)
            // sim = _sim;
            // HighLevelSimulator sim = usersSimulator.get(sessionId);
            if (restartSim) {
                if (_sim != null) {
                    _sim.destroy();
                    createLogContainer.destroy();
                    tokenController.destroy();
                }
                CleanOutputPathContent(sessionId);
                _sim = HighLevelSimulator.getHighLevelSimulator(SimulatorService.getInstance().getNewSimulator());
                createLogContainer = CreateLogContainer.getInstance().getNewContainer(net);
                tokenController = TokenController.getInstance().getNewController();
            } else if (_sim == null)
                _sim = HighLevelSimulator.getHighLevelSimulator(SimulatorService.getInstance().getNewSimulator());
                createLogContainer = CreateLogContainer.getInstance().getNewContainer(net);
            tokenController = TokenController.getInstance().getNewController();

            Checker checker = new Checker(net, null, _sim);

            String pathStr = getOutputFullPathStr(sessionId);

            log.debug("Prepare path for writing reports to: " + pathStr);            
            checker.checkInitializing(pathStr, pathStr);

            usersCheckers.put(sessionId, checker);
            usersSimulator.put(sessionId, _sim);
        }
    }

    private String getOutputFullPathStr(String sessionId) {
        String outputPathStr = FilenameUtils.concat(System.getProperty("user.home"), "CPN_IDE");
        outputPathStr = FilenameUtils.concat(outputPathStr, OUTPUT_MODEL_PATH);
        outputPathStr = FilenameUtils.concat(outputPathStr, sessionId);

        String pathStr = Paths.get(outputPathStr).toAbsolutePath().toString();
        return pathStr;
    }

    private List<IssueDescription> getOrCreateIssueList(String id, Map<String, List<IssueDescription>> issues) {
        List<IssueDescription> issList = issues.get(id);
        if (issList == null) {
            issList = new ArrayList<>();
            issues.put(id, issList);
        }

        return issList;
    }

    public String exportNetToXml(String sessionId) throws Exception {
        synchronized (lock) {
            PetriNet net = usersNets.get(sessionId);
            if (net == null)
                throw new NotFoundException("Session object not found");

            Document xmlDoc = DOMGenerator.export(net);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            try (StringWriter writer = new StringWriter()) {
                StreamResult result = new StreamResult(writer);
                transformer.transform(new DOMSource(xmlDoc), result);
                return writer.toString();
            }
        }

    }

    public NewMonitorDescr getNewMonitor(String sessionId, MonitorDescr descr) throws Exception {
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        PetriNet net = usersNets.get(sessionId);
        List<Node> nodes = new ArrayList<>();

        Set<String> ids = new HashSet<>(Arrays.asList(descr.getNodes()));

        for (Instance<Transition> tis : sim.getAllTransitionInstances()) {
            if (ids.contains(tis.getNode().getId()))
                nodes.add(tis.getNode());

        }

        for (Instance<PlaceNode> p : sim.getAllPlaceInstances()) {
            if (ids.contains(p.getNode().getId()))
                nodes.add(p.getNode());
        }

        MonitorTemplate template = MonitorTemplateFactory.createMonitorTemplate(MonitorTypes.valueOf(descr.getType()));
        return NewMonitorDescr.builder().defaultInit(template.defaultInit(sim, net, nodes))
                .defaultObserver(template.defaultObserver(sim, net, nodes))
                .defaultPredicate(template.defaultPredicate(sim, net, nodes))
                .defaultStop(template.defaultStop(sim, net, nodes)).defaultTimed(template.defaultTimed(sim, net, nodes))
                .build();
    }

    public void InitSimulator(String sessionId, Options options) throws Exception {
        synchronized (lock) {
            PetriNet net = usersNets.get(sessionId);
            if (net == null)
                throw new NotFoundException("Session object not found");

            HighLevelSimulator sim = usersSimulator.get(sessionId);
            // if (sim != null) {
            // sim.destroy();
            // }
            ////
            // sim =
            // HighLevelSimulator.getHighLevelSimulator(SimulatorService.getInstance().getNewSimulator());

            // usersSimulator.put(sessionId, sim);
            boolean fairnessBE = false;
            boolean fairnessGlobalBE = false;

            try {
                fairnessBE = Boolean.parseBoolean(options.getOptions().get("fair_be"));
            }catch (Exception ignored){}
            try {
                fairnessGlobalBE = Boolean.parseBoolean(options.getOptions().get("global_fairness"));
            }catch (Exception ignored){}

            Checker checker = usersCheckers.get(sessionId);// new Checker(net, null, sim);

            // checker.checkEntireModel();

            // checker.localCheck();
            // CleanOutputPathContent(sessionId);

            // String file = Paths.get(FilenameUtils.concat(OUTPUT_MODEL_PATH,
            // sessionId)).toAbsolutePath().toString();
            // checker.checkInitializing(file, file);
            checker.checkDeclarations();
            checker.generateSerializers();
            checker.checkPages();
            checker.generatePlaceInstances();
            log.debug("Checking monitors");
            checker.checkMonitors();
            log.debug("Checking monitors is OK");
            checker.generateNonPlaceInstances();
            checker.initialiseSimulationScheduler();
            // checker.instantiateSMLInterface();
            sim.setTarget((org.cpntools.accesscpn.model.impl.PetriNetImpl) net);

            sim.initialState();
            sim.refreshViews();

            if(fairnessBE || fairnessGlobalBE)
                sim.setFairnessOptions(fairnessBE, fairnessGlobalBE);
            // checker.instantiateSMLInterface();

            // usersSimulator.put(sessionId, sim);

        }

    }

    //TODO make this also for createLog
     ReplicationResp getOutputPathContent(String sessionId) throws Exception {
        String pathStr = getOutputFullPathStr(sessionId);

        final StringBuilder sb = new StringBuilder();
        final List<HtmlFileContent> htmlContent  = new ArrayList<>();
        List<Path> files = Files
            .walk(Paths.get(pathStr).toAbsolutePath())
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".html"))
            .collect(Collectors.toList());

        for (Path f : files) {
            sb.append("\n\nFilename: " + f.toString() + "\n");
            final StringBuilder fileHtmlContent = new StringBuilder();

            Files.lines(f).forEach(s -> {
                sb.append(s + "\n");
                fileHtmlContent.append(s + "\n");
            });
            htmlContent.add(new HtmlFileContent(f.toString(), fileHtmlContent.toString()));
        }
        ReplicationResp outputPathContent = new ReplicationResp();
        outputPathContent.setExtraInfo(sb.toString());
        outputPathContent.setFiles(htmlContent);
        return outputPathContent;
    }

    CreateLogResp getOutputPathLogs(String sessionId) throws Exception {
        String pathStr = getOutputFullPathStr(sessionId);

        final StringBuilder sb = new StringBuilder();
        final List<HtmlFileContent> htmlContent  = new ArrayList<>();
        List<Path> files = Files
                .walk(Paths.get(pathStr).toAbsolutePath())
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".html"))
                .collect(Collectors.toList());

        for (Path f : files) {
            sb.append("\n\nFilename: " + f.toString() + "\n");
            final StringBuilder fileHtmlContent = new StringBuilder();

            Files.lines(f).forEach(s -> {
                sb.append(s + "\n");
                fileHtmlContent.append(s + "\n");
            });
            htmlContent.add(new HtmlFileContent(f.toString(), fileHtmlContent.toString()));
        }
        CreateLogResp outputPathContent = new CreateLogResp();
        outputPathContent.setExtraInfo(sb.toString());
        outputPathContent.setFiles(htmlContent);
        return outputPathContent;
    }


    public ReplicationProgressResp getFilesForReplicationProgress(String sessionId, Replication replicationParams) throws Exception{
        String pathStr = getOutputFullPathStr(sessionId);
        ReplicationProgressResp replicationProgressResp = new ReplicationProgressResp();
        String[] files = Arrays.stream(new File(pathStr).list()).filter(s -> s.startsWith("reps_")).toArray(String[]::new);
        if(files.length > 0) {
            String curReplicationFolderPath = files[files.length - 1];
            String[] simfiles = Arrays.stream((new File(pathStr + "/" + curReplicationFolderPath)).list()).filter(s -> s.startsWith("sim_")).toArray(String[]::new);;
            replicationProgressResp.setProgress("" + (100.0 * simfiles.length / Integer.valueOf(replicationParams.getRepeat())));
            return replicationProgressResp;
        } else {
            replicationProgressResp.setProgress("0");
            return replicationProgressResp;
        }
    }

    public CreateLogProgressResp getFilesForCreateLogProgress(String sessionId, CreateLogFromReplications createLogParams) throws Exception{
        String pathStr = getOutputFullPathStr(sessionId);
        CreateLogProgressResp createLogProgressResp = new CreateLogProgressResp();
        String[] files = Arrays.stream(new File(pathStr).list()).filter(s -> s.startsWith("reps_")).toArray(String[]::new);
        if(files.length > 0) {
            String curCreateLogFolderPath = files[files.length-1];
            String[] simfiles = Arrays.stream((new File(pathStr+ "/" + curCreateLogFolderPath)).list()).filter(s -> s.startsWith("sim_")).toArray(String[]::new);;
            createLogProgressResp.setProgress("" + (100.0 * simfiles.length / Integer.valueOf(createLogParams.getRepeat())));
            return createLogProgressResp;
        } else {
            createLogProgressResp.setProgress("0");
            return createLogProgressResp;
        }
    }




    void CleanOutputPathContent(String sessionId) throws Exception {
        String pathStr = getOutputFullPathStr(sessionId);
        FileUtils.deleteDirectory(new File(pathStr));
    }

    List<String> getEnableTransitionsImpl(String sessionId) throws Exception {
        synchronized (lock) {
            HighLevelSimulator s = usersSimulator.get(sessionId);
            if (s == null)
                throw new NotFoundException("Session object not found");
            // Checker checker = new Checker(net, new File("C:\\tmp\\cpn.file"), sim);
            List<String> arr = new ArrayList<>();
            while (true) {
                List<Instance<Transition>> tis = s.getAllTransitionInstances();

                for (Instance<Transition> ti : tis) {

                    if (s.isEnabled(ti))
                        arr.add(ti.getNode().getId());
                }
                if (arr.isEmpty()) {
                    String res = s.increaseTime();
                    if (res != null) // sim ended
                        return arr;
                } else
                    break;
            }

            return arr;
        }
    }

    public List<String> getEnableTransitions(String sessionId) throws Exception {
        return getEnableTransitionsImpl(sessionId);
    }

    public List<String> returnEnableTrans(String sessionId) throws Exception {
        NetInfo netInf = this.netInf.get(sessionId);
        if (netInf != null)
            return this.netInf.get(sessionId).getEnableTrans();
        else
            return getEnableTransitions(sessionId);
    }

    public List<PlaceMark> returnTokensAndMarking(String sessionId) throws Exception {
        NetInfo netInf = this.netInf.get(sessionId);
        if (netInf != null)
            return this.netInf.get(sessionId).getTokensAndMark();
        else
            return getTokensAndMarking(sessionId);
    }

    public List<PlaceMark> getTokensAndMarking(String sessionId) throws Exception {
        synchronized (lock) {
            HighLevelSimulator s = usersSimulator.get(sessionId);
            if (s == null)
                throw new NotFoundException("Session object not found");

            List<PlaceMark> result = new ArrayList<>();
            for (Instance<PlaceNode> p : s.getAllPlaceInstances()) {
                int tokens = s.getTokens(p);
                String marking = s.getMarking(p);
                result.add(PlaceMark.builder().id(p.getNode().getId()).marking(marking).tokens(tokens).build());
            }
            return result;
        }
    }

    public Map<String, List<IssueDescription>> PerfomEntireChecking(String sessionId) throws Exception {

        Checker checker = usersCheckers.get(sessionId);
        PetriNet net = usersNets.get(sessionId);
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        if (net == null || checker == null || sim == null)
            throw new NotFoundException("Session object not found");

        Map<String, List<IssueDescription>> issues = new HashMap<>();

        try {
            //
            // checker.checkEntireModel();
            // checker.checkInitializing("", "");
            checker.checkDeclarations();
            checker.generateSerializers();
            checker.checkPages();
            checker.generatePlaceInstances();
            // checker.checkMonitors();
            log.debug("Checking monitors");
            for (final Monitor m : net.getMonitors())
                CheckMonitor(checker, m, issues);
            log.debug("Checking monitors - OK");
            checker.generateNonPlaceInstances();
            checker.initialiseSimulationScheduler();

            // sim.setTarget((org.cpntools.accesscpn.model.impl.PetriNetImpl) net);
            // sim.refreshViews();

            // checker.instantiateSMLInterface();
        } catch (CheckerException ex) {
            SplitMessageForIssues(ex, issues);
        }

        return issues;
    }

    void SplitMessageForIssues(CheckerException ex, Map<String, List<IssueDescription>> issues) {
        String[] lines = ex.getMessage().split("\\n");
        for (String ll : lines) {
            String[] pair = ll.split(":");
            if (pair.length == 2) {
                List<IssueDescription> issList = getOrCreateIssueList(pair[0], issues);
                issList.add(IssueDescription.builder().type(IssueTypes.PAGE.getType()).id(pair[0]).description(pair[1])
                        .build());
            } else {
                List<IssueDescription> issList = getOrCreateIssueList(ex.getId(), issues);
                issList.add(IssueDescription.builder().type(IssueTypes.PAGE.getType()).id(ex.getId()).description(ll)
                        .build());
            }
        }
    }

    public Map<String, List<IssueDescription>> PerfomEntireCheckingFast(String sessionId) throws Exception {
        synchronized (lock) {
            Checker checker = usersCheckers.get(sessionId);
            // HighLevelSimulator ss = usersSimulator.get(sessionId);
            PetriNet net = usersNets.get(sessionId);
            if (net == null || checker == null)
                throw new NotFoundException("Session object not found");

            Map<String, List<IssueDescription>> issues = new HashMap<>();

            for (final HLDeclaration decl : net.declaration())
                CheckDelaration(checker, decl, issues);

            final PageSorter ps = new PageSorter(net.getPage());
            for (final Page page : ps)
                CheckPage(checker, page, ps.isPrime(page), issues);

            for (final Monitor m : net.getMonitors())
                CheckMonitor(checker, m, issues);

            return issues;

        }
    }

    public void AddDeclaration(String sessionId, HLDeclaration declaration) throws Exception {
        synchronized (lock) {
            PetriNet net = usersNets.get(sessionId);
            if (net == null)
                throw new NotFoundException("Session object not found");
            declaration.setParent(net);
        }
    }

    public void DeleteDeclaration(String sessionId, String id) throws Exception {
        synchronized (lock) {
            PetriNet net = usersNets.get(sessionId);
            if (net == null)
                throw new NotFoundException("Session object not found");

            HLDeclaration decltToDelete = null;
            for (final HLDeclaration decl : net.declaration()) {
                if (decl.getId().equals(id)) {
                    decltToDelete = decl;
                    break;
                }
            }

            if (decltToDelete == null)
                throw new NotFoundException("Declaration with id: " + id + ", not found");

            decltToDelete.setParent(null);
        }
    }

    public Map<String, List<IssueDescription>> CheckPageByID(String sessionId, String id) throws Exception {
        synchronized (lock) {
            Checker checker = usersCheckers.get(sessionId);
            PetriNet net = usersNets.get(sessionId);
            if (net == null || checker == null)
                throw new NotFoundException("Session object not found");

            Map<String, List<IssueDescription>> issues = new HashMap<>();
            final PageSorter ps = new PageSorter(net.getPage());
            for (final Page page : ps) {
                if (page.getId().equals(id)) {
                    CheckPage(checker, page, ps.isPrime(page), issues);
                    return issues;
                }
            }
            return issues;
        }
    }

    public Map<String, List<IssueDescription>> CheckDeclarationByID(String sessionId, String id) throws Exception {
        synchronized (lock) {
            Checker checker = usersCheckers.get(sessionId);
            PetriNet net = usersNets.get(sessionId);
            if (net == null || checker == null)
                throw new NotFoundException("Session object not found");

            Map<String, List<IssueDescription>> issues = new HashMap<>();

            for (final HLDeclaration decl : net.declaration()) {
                if (decl.getId().equals(id)) {
                    CheckDelaration(checker, decl, issues);
                    return issues;
                }
            }
            return issues;
        }
    }

    public Map<String, List<IssueDescription>> CheckMonitorByID(String sessionId, String id) throws Exception {
        synchronized (lock) {
            Checker checker = usersCheckers.get(sessionId);
            // HighLevelSimulator ss = usersSimulator.get(sessionId);
            PetriNet net = usersNets.get(sessionId);
            if (net == null || checker == null)
                throw new NotFoundException("Session object not found");

            Map<String, List<IssueDescription>> issues = new HashMap<>();

            for (final Monitor m : net.getMonitors()) {
                if (m.getId().equals(id)) {
                    CheckMonitor(checker, m, issues);
                    return issues;
                }
            }
            return issues;
        }
    }

    private void CheckMonitor(Checker checker, Monitor m, Map<String, List<IssueDescription>> issues)
            throws IOException {
        synchronized (lock) {
            try {
                checker.checkMonitor(m);
            } catch (SyntaxCheckerException ex) {
                List<IssueDescription> issList = getOrCreateIssueList(m.getId(), issues);
                issList.add(IssueDescription.builder().type(IssueTypes.MONITOR.getType()).id(ex.getId())
                        .description(ex.getMessage()).build());
            }
        }
    }

    private void CheckDelaration(Checker checker, HLDeclaration decl, Map<String, List<IssueDescription>> issues)
            throws IOException {
        log.debug("After lock: CheckDelaration");
        try {
            checker.checkDeclaration(decl);
        } catch (DeclarationCheckerException ex) {
            List<IssueDescription> issList = getOrCreateIssueList(decl.getId(), issues);
            issList.add(IssueDescription.builder().id(ex.getId()).type(IssueTypes.DECLARATION.getType())
                    .description(ex.getMessage()).build());
        }
    }

    private void CheckPage(Checker checker, Page page, boolean prime, Map<String, List<IssueDescription>> issues)
            throws IOException {
        try {
            checker.checkPage(page, prime);
        } catch (CheckerException ex) {
            String[] lines = ex.getMessage().split("\\n");
            for (String ll : lines) {
                String[] pair = ll.split(":");
                if (pair.length == 2) {
                    List<IssueDescription> issList = getOrCreateIssueList(pair[0], issues);
                    issList.add(IssueDescription.builder().type(IssueTypes.PAGE.getType()).id(pair[0])
                            .description(pair[1]).build());
                } else {
                    List<IssueDescription> issList = getOrCreateIssueList(page.getId(), issues);
                    issList.add(IssueDescription.builder().type(IssueTypes.PAGE.getType()).id(ex.getId())
                            .description(ll).build());
                }
            }
        }
    }

    Instance<Transition> getTargetTransition(HighLevelSimulator s, String transId) throws Exception {
        Instance<Transition> targetTransition = null;
        List<Instance<Transition>> tis = s.getAllTransitionInstances();
        for (Instance<Transition> ti : tis) {
            if (ti.getNode().getId().equals(transId)) {
                targetTransition = ti;
                break;
            }
        }
        if (targetTransition == null)
            throw new Exception("Can't find transiton");
        return targetTransition;
    }

    Map<String, Binding> getBindingForTransiton(HighLevelSimulator s, String transId) throws Exception {
        List<Binding> bs = s.getBindings(getTargetTransition(s, transId));
        Map<String, Binding> res = new HashMap<>();
        for (Binding b : bs) {
            StringBuilder sb = new StringBuilder();
            boolean second = false;
            for (ValueAssignment va : b.getAllAssignments()) {
                if (second)
                    sb.append(",");

                sb.append(va.getName());
                sb.append("=");
                sb.append(va.getValue());

                second = true;
            }
            res.put(sb.toString(), b);
        }
        return res;
    }

    public BindingMark[] getBindings(String sessionId, String transId) throws Exception {
        HighLevelSimulator s = usersSimulator.get(sessionId);

        List<Binding> bs = s.getBindings(getTargetTransition(s, transId));
        Map<String, Binding> binds = getBindingForTransiton(s, transId);
        return binds.keySet().stream().map(k -> new BindingMark(k)).toArray(BindingMark[]::new);
    }

    public Binding getAnyBindingFromMap(Map<String, Binding> bindings) throws Exception {
        Map.Entry<String,Binding> entry = bindings.entrySet().iterator().next();
        System.out.println(bindings.size());
        Binding binding = entry.getValue();
        //TODO MAKE RANDOM
        return binding;
    }




    public String makeStep(String sessionId, String transId) throws Exception {
        // String type = requestBody.get(0).get("type").toString();
        Binding b = null;
        HighLevelSimulator s = usersSimulator.get(sessionId);
        if (transId.equals("multistep")) {
            b = s.executeAndGet();
        } else {
            b = s.executeAndGet(getTargetTransition(s, transId));
        }

        updateCreateLog(sessionId, b);

        return b.getTransitionInstance().getNode().getId();
    }

    public void updateCreateLog(String sessionId, Binding b) throws Exception{
        if(createLogContainer.isRecordingTime()) {
            tokenController.updateMarking(returnTokensAndMarking(sessionId));
        }
        Double timeLastChanged = tokenController.getLastTime();
        createLogContainer.recordActivity(b, getState(sessionId), timeLastChanged);
    }

    public SimInfo getState(String sessionId) throws Exception {
        // String type = requestBody.get(0).get("type").toString();
        HighLevelSimulator s = usersSimulator.get(sessionId);
        return SimInfo.builder().step(s.getStep().longValueExact()).time(s.getTime()).build();
    }

    public XLog getLog(String sessionId) {
        return createLogContainer.getLog();
    }

    public String getOutputPath() { return createLogContainer.getOutputPath(); }

    public void setOutputPath(String sessionId, String path) {
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        createLogContainer.setOutputPath(path, sim.getOutputDir());
    }

    public Boolean isLogEmpty(String sessionId) {
        return createLogContainer.isLogEmpty();
    }

    public void makeStepWithBinding(String sessionId, String bindingId, String transId) throws Exception {
        // String type = requestBody.get(0).get("type").toString();
        HighLevelSimulator s = usersSimulator.get(sessionId);
        Map<String, Binding> binds = getBindingForTransiton(s, transId);
        s.execute(binds.get(bindingId));
        SimInfo simInfo = getState(sessionId);
        List<PlaceMark> marking = getTokensAndMarking(sessionId);

        updateCreateLog(sessionId, binds.get(bindingId));
    }

    public ReplicationResp makeReplication(String sessionId, Replication stepParam) throws Exception {
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        log.debug("Writing report to " + sim.getOutputDir());
        File fileObj = new File(sim.getOutputDir());
        fileObj.mkdirs();
        sim.evaluate("CPN'Replications.nreplications " + stepParam.getRepeat());
        log.debug("Written report to " + sim.getOutputDir());
        return getOutputPathContent(sessionId);
    }

    public ReplicationResp makeCreateLog(String sessionId, CreateLogConfig config) throws Exception {
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        File fileObj = new File(sim.getOutputDir());
        fileObj.mkdirs();
        log.debug("Writing log to " + sim.getOutputDir());
        createLogContainer.CreateLog(config, tokenController.getLastTime());
        log.debug("Written log to " + sim.getOutputDir());
        return getOutputPathContent(sessionId);
    }

    public String runScript(String sessionId, Replication stepParam) throws Exception {
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        log.debug("Writing report to " + sim.getOutputDir());
        File fileObj = new File(sim.getOutputDir());
        fileObj.mkdirs();
        sim.evaluate(stepParam.getRepeat());
        log.debug("Written report to " + sim.getOutputDir());
        return getOutputPathContent(sessionId).getExtraInfo();
    }

    public String makeStepFastForward(String sessionId, MultiStep stepParam) throws Exception {
        //String type = RequestBody.get(0).get("type").toString();
        HighLevelSimulator sim = usersSimulator.get(sessionId);
        int i = 0;
        String simulationEnded = "";
        int maxSteps = stepParam.getAmount();
        while (i < maxSteps) {
            List<Instance<Transition>> enabled = new ArrayList<>();
            List<Instance<Transition>> all = sim.getAllTransitionInstances();
            for (Instance<Transition> ti : all) {
                if (sim.isEnabled(ti))
                    enabled.add(ti);
            }

            if (enabled.isEmpty()) {
                String result = sim.increaseTime();
                if (result == null) {
                    continue; // --> go back and check for enabled transitions
                } else {
                    simulationEnded = result;
                    break; // end/stop simulation, report result to user
                }
            }

            Binding b = sim.executeAndGet();
            List<PlaceMark> marking = getTokensAndMarking(sessionId);

            updateCreateLog(sessionId, b);
            i++;
        }
        //_sim.execute(stepParam.getAmount());

        return getOutputPathContent(sessionId).getExtraInfo();
    }

    public void setRecordActivities(Boolean bool){
        createLogContainer.setRecordActivities(bool);
    }

    public void setRecordTime(Boolean bool) {
        System.out.println("recordTime is set to " + bool);
        createLogContainer.setRecordTime(bool);
    }

    public void clearLog(){
        createLogContainer.clearLog();
    }

}
