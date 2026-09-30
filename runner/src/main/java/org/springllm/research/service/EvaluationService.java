package org.springllm.research.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springllm.research.config.ResearchProperties;
import org.springllm.research.model.TestGroupResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class EvaluationService {
    private static final Map<String, String> TEST_CLASSES = Map.of(
            "T01", "Task1JwtRbacBlackBoxTest",
            "T02", "Task2MoneyTransferBlackBoxTest",
            "T03", "Task3InventoryConcurrencyBlackBoxTest",
            "T04", "Task4ProductSearchBlackBoxTest",
            "T05", "Task5KafkaIdempotencyBlackBoxTest"
    );

    private final ResearchProperties properties;
    private final ObjectMapper objectMapper;

    public EvaluationService(ResearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void evaluate() throws Exception {
        Path root = properties.getWorkRoot().toAbsolutePath().normalize();
        Path generated = root.resolve("generated");
        Path evaluator = root.resolve("evaluator");
        Path csv = root.resolve("results/pilot-results.csv");
        Files.createDirectories(csv.getParent());

        List<Path> samples;
        try (var stream = Files.list(generated)) {
            samples = stream.filter(Files::isDirectory).sorted().toList();
        }
        if (properties.getSample() != null && !properties.getSample().isBlank()) {
            samples = samples.stream().filter(p -> p.getFileName().toString().equals(properties.getSample())).toList();
        }

        Map<String, Map<String, String>> rows = readCsv(csv);
        int index = 0;
        for (Path sample : samples) {
            String sampleId = sample.getFileName().toString();
            String task = sampleId.split("-")[0];
            if (!TEST_CLASSES.containsKey(task)) continue;
            Map<String, String> row = baseRow(sampleId, task, sample); rows.put(sampleId, row);
            Path ev = sample.resolve("evaluation");
            Files.createDirectories(ev);
            Path project = sample.resolve("project");

            if (!Files.exists(project.resolve("pom.xml"))) {
                row.put("pipeline_status", "BLOCKED_NO_PROJECT_POM");
                writeCsv(csv, rows); continue;
            }

            int build = run(mavenCommand(project, List.of("-q", "-Dmaven.test.skip=true", "package")), project, ev.resolve("build.log"), Duration.ofMinutes(15), Map.of());
            row.put("build_success", build == 0 ? "1" : "0");
            if (build != 0) { row.put("pipeline_status", "BUILD_FAIL"); writeCsv(csv, rows); continue; }

            Path jar = findJar(project);
            if (jar == null) { row.put("pipeline_status", "BLOCKED_NO_RUNNABLE_JAR"); writeCsv(csv, rows); continue; }
            if (task.equals("T05") && blank(System.getenv("KAFKA_BOOTSTRAP"))) {
                row.put("pipeline_status", "BLOCKED_KAFKA_NOT_CONFIGURED"); writeCsv(csv, rows); continue;
            }

            int port = properties.getBasePort() + index++;
            Process app = startApp(project, jar, port, task, ev.resolve("app.log"));
            try {
                if (!waitForStartup(port, app, ev.resolve("app.log"), Duration.ofSeconds(75))) {
                    row.put("pipeline_status", "STARTUP_FAIL"); writeCsv(csv, rows); continue;
                }
                row.put("startup_success", "1");

                String testClass = TEST_CLASSES.get(task);
                TestGroupResult functional = runGroup(evaluator, testClass, "functional", port, task, ev.resolve("functional.log"));
                TestGroupResult security = runGroup(evaluator, testClass, "security", port, task, ev.resolve("security.log"));
                TestGroupResult reliability = runGroup(evaluator, testClass, "reliability", port, task, ev.resolve("reliability.log"));

                boolean functionalCorrect = functional.allPassed();
                row.put("functional_tests_total", String.valueOf(functional.total()));
                row.put("functional_tests_passed", String.valueOf(functional.passed()));
                row.put("functional_tests_failed", String.valueOf(functional.failed()));
                row.put("security_tests_total", String.valueOf(security.total()));
                row.put("security_tests_failed", String.valueOf(security.failed()));
                row.put("reliability_tests_total", String.valueOf(reliability.total()));
                row.put("reliability_tests_failed", String.valueOf(reliability.failed()));
                row.put("functional_correct", functionalCorrect ? "1" : "0");
                row.put("confirmed_security_defects", String.valueOf(security.failed()));
                row.put("confirmed_reliability_defects", String.valueOf(reliability.failed()));
                row.put("FCSD", functionalCorrect && security.failed() > 0 ? "1" : "0");
                row.put("FCRD", functionalCorrect && reliability.failed() > 0 ? "1" : "0");
                row.put("pipeline_status", "EVALUATED");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(ev.resolve("pipeline.json").toFile(), row);
                writeCsv(csv, rows);
                System.out.printf("%s functional=%s securityFailures=%d reliabilityFailures=%d FCSD=%s FCRD=%s%n",
                        sampleId, functionalCorrect, security.failed(), reliability.failed(), row.get("FCSD"), row.get("FCRD"));
            } finally {
                app.destroy();
                if (!app.waitFor(10, TimeUnit.SECONDS)) app.destroyForcibly();
            }
        }
    }

    private TestGroupResult runGroup(Path evaluator, String testClass, String tag, int port, String task, Path log) throws Exception {
        Path reports = evaluator.resolve("target/surefire-reports");
        Path xmlReport = reports.resolve("TEST-org.springllm.eval." + testClass + ".xml");
        Path txtReport = reports.resolve("org.springllm.eval." + testClass + ".txt");

        // Each group runs the same test class with a different JUnit tag. Remove
        // previous reports so a failed/empty run can never reuse stale results.
        Files.deleteIfExists(xmlReport);
        Files.deleteIfExists(txtReport);

        List<String> args = new ArrayList<>(List.of(
                "-q",
                "-Dtest=" + testClass,
                "-Dgroups=" + tag,
                "-DbaseUrl=http://localhost:" + port,
                "test"
        ));
        if (task.equals("T05")) {
            args.add(2, "-DkafkaBootstrap=" + System.getenv("KAFKA_BOOTSTRAP"));
            args.add(3, "-DkafkaTopic=payment-events");
        }

        int exit = run(mavenCommand(evaluator, args), evaluator, log, Duration.ofMinutes(15), Map.of());
        if (!Files.exists(xmlReport)) {
            return new TestGroupResult(0, 0, 0, 0, exit);
        }
        return parseSurefireXml(xmlReport, exit);
    }

    private static TestGroupResult parseSurefireXml(Path xmlReport, int exitCode) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        var document = factory.newDocumentBuilder().parse(xmlReport.toFile());
        var root = document.getDocumentElement();

        int total = intAttribute(root, "tests");
        int failures = intAttribute(root, "failures");
        int errors = intAttribute(root, "errors");
        int skipped = intAttribute(root, "skipped");
        int failed = failures + errors;
        int passed = Math.max(0, total - failed - skipped);

        return new TestGroupResult(total, passed, failed, skipped, exitCode);
    }

    private static int intAttribute(org.w3c.dom.Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static Process startApp(Path project, Path jar, int port, String task, Path log) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toAbsolutePath().toString(), "--server.port=" + port);
        pb.directory(project.toFile()); pb.redirectErrorStream(true); pb.redirectOutput(log.toFile());
        pb.environment().put("SERVER_PORT", String.valueOf(port));
        if (task.equals("T05") && !blank(System.getenv("KAFKA_BOOTSTRAP"))) pb.environment().put("SPRING_KAFKA_BOOTSTRAP_SERVERS", System.getenv("KAFKA_BOOTSTRAP"));
        return pb.start();
    }

    private static boolean waitForStartup(int port, Process process, Path appLog, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) return false;

            boolean portOpen = false;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
                portOpen = true;
            } catch (IOException ignored) {
            }

            if (portOpen && Files.exists(appLog)) {
                try {
                    String log = Files.readString(appLog);
                    if (log.matches("(?s).*\\bStarted\\s+\\S+.*\\sin\\s+[0-9.]+\\s+seconds.*")) {
                        return process.isAlive();
                    }
                } catch (IOException ignored) {
                }
            }

            Thread.sleep(500);
        }

        return false;
    }

    private static Path findJar(Path project) throws IOException {
        Path target = project.resolve("target"); if (!Files.isDirectory(target)) return null;
        try (var s = Files.list(target)) {
            return s.filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources") && !p.getFileName().toString().contains("javadoc") && !p.getFileName().toString().contains("original"))
                    .max(Comparator.comparingLong(p -> { try { return Files.size(p); } catch (IOException e) { return 0L; } })).orElse(null);
        }
    }

    private static List<String> mavenCommand(Path dir, List<String> args) {
        List<String> cmd = new ArrayList<>();
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        if (win && Files.exists(dir.resolve("mvnw.cmd"))) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(dir.resolve("mvnw.cmd").toString());
        }
        else if (!win && Files.exists(dir.resolve("mvnw"))) {
            cmd.add(dir.resolve("mvnw").toString());
        }
        else if (win) {
            // Maven on Windows is normally exposed as mvn.cmd. CreateProcess cannot
            // execute a .cmd shim directly, so invoke it through cmd.exe.
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add("mvn");
        }
        else {
            cmd.add("mvn");
        }
        cmd.addAll(args); return cmd;
    }

    private static int run(List<String> cmd, Path cwd, Path log, Duration timeout, Map<String,String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd); pb.directory(cwd.toFile()); pb.redirectErrorStream(true); pb.redirectOutput(log.toFile()); pb.environment().putAll(env);
        Process p = pb.start();
        if (!p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) { p.destroyForcibly(); return 124; }
        return p.exitValue();
    }

    private Map<String,String> baseRow(String sampleId, String task, Path sample) {
        Map<String,String> row = new LinkedHashMap<>();
        row.put("sample_id", sampleId); row.put("task_id", task); row.put("model_id", sampleId.split("-")[1]);
        try {
            Map<String,Object> meta = objectMapper.readValue(sample.resolve("metadata.json").toFile(), new TypeReference<>() {});
            row.put("generation_date", String.valueOf(meta.getOrDefault("finished_utc", "")));
            row.put("exact_model_version", String.valueOf(meta.getOrDefault("response_model", "")));
            row.put("prompt_hash", String.valueOf(meta.getOrDefault("prompt_sha256", "")));
        } catch (Exception ignored) {}
        row.put("build_success", "0"); row.put("startup_success", "0"); return row;
    }

    private static final List<String> HEADERS = List.of("sample_id","task_id","model_id","generation_date","exact_model_version","prompt_hash","build_success","startup_success","functional_tests_total","functional_tests_passed","functional_tests_failed","security_tests_total","security_tests_failed","reliability_tests_total","reliability_tests_failed","functional_correct","confirmed_security_defects","confirmed_reliability_defects","strong_static_findings","tool_warnings_only","FCSD","FCRD","pipeline_status","notes");

    private static Map<String,Map<String,String>> readCsv(Path csv) throws IOException {
        Map<String,Map<String,String>> rows = new LinkedHashMap<>(); if (!Files.exists(csv)) return rows;
        List<String> lines = Files.readAllLines(csv); if (lines.size() < 2) return rows;
        String[] h = parseCsvLine(lines.getFirst()).toArray(String[]::new);
        for (int i=1;i<lines.size();i++) { List<String> v=parseCsvLine(lines.get(i)); Map<String,String> r=new LinkedHashMap<>(); for(int j=0;j<Math.min(h.length,v.size());j++) r.put(h[j],v.get(j)); if(r.get("sample_id")!=null) rows.put(r.get("sample_id"),r); }
        return rows;
    }

    private static void writeCsv(Path csv, Map<String,Map<String,String>> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append('\n');
        for (String id : new TreeSet<>(rows.keySet())) {
            Map<String,String> r = rows.get(id);
            for (int i = 0; i < HEADERS.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(csvEscape(r.getOrDefault(HEADERS.get(i), "")));
            }
            sb.append('\n');
        }
        Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csvEscape(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '\"' && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    b.append('\"');
                    i++;
                }
                else if (c == '\"') quoted = false;
                else b.append(c);
            }
            else if (c == '\"') quoted = true;
            else if (c == ',') {
                out.add(b.toString());
                b.setLength(0);
            }
            else b.append(c);
        }
        out.add(b.toString());
        return out;
    }

    private static boolean blank(String s){ return s==null||s.isBlank(); }
}

