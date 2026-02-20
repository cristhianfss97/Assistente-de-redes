package com.assistente;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * AssistenteRedesApp (JavaFX)
 * --------------------------
 * App desktop de diagnóstico de rede.
 *
 * Ideias-chave:
 *  - UI em JavaFX (TabPane + Sidebar)
 *  - Execução assíncrona: comandos e testes rodam em thread pool (não trava interface)
 *  - Saída sempre no painel Output (TextArea)
 *  - Histórico persistido em arquivo (JSONL), com exportação CSV
 *  - Preferência por implementações "puras" em Java quando possível (Interfaces de rede, HTTP, SSL)
 *  - Para itens que dependem do SO (Rotas/ARP/Netstat/Traceroute em alguns casos), usamos comandos do sistema.
 */
public class AssistenteRedesApp extends Application {

    // Pool para rodar tarefas em background
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    // UI: saída e status
    private TextArea output;
    private Label statusHost, statusIp, statusOs;

    // Histórico
    private TableView<HistRow> historyTable;
    private ComboBox<String> historyAction;
    private TextField historySearch;
    private Spinner<Integer> historyLimit;

    // Arquivo de histórico (um JSON por linha)
    private static final Path APP_DIR = getAppDir();
    private static final Path HISTORY = APP_DIR.resolve("history.jsonl");
    private static final Path HISTORY_CSV = APP_DIR.resolve("history.csv");

    // Tabs (conteúdo principal)
    private TabPane tabs;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Assistente de Redes");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("rootpane");

        root.setTop(buildHeader());

        // Sidebar com rolagem (p/ telas menores)
        ScrollPane sideScroll = new ScrollPane(buildSidebar());
        sideScroll.setFitToWidth(true);
        sideScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sideScroll.getStyleClass().add("sideScroll");
        root.setLeft(sideScroll);

        // Conteúdo principal com rolagem
        ScrollPane mainScroll = new ScrollPane(buildMain());
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScroll.getStyleClass().add("mainScroll");
        root.setCenter(mainScroll);

        Scene scene = new Scene(root, 1050, 700);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.show();

        // Carregamentos iniciais
        Platform.runLater(this::refreshStatus);
        Platform.runLater(this::refreshHistory);
        Platform.runLater(this::verifyTools);
    }

    // ---------------- HEADER ----------------
    private HBox buildHeader(){
        VBox titles = new VBox();
        Label h1 = new Label("Assistente de Redes");
        h1.getStyleClass().add("h1");
        Label sub = new Label("Troubleshooting • DNS • HTTP/HTTPS • SSL • Rotas • Portas • Conectividade • Sem login");
        sub.getStyleClass().add("muted");
        titles.getChildren().addAll(h1, sub);
        titles.setSpacing(2);

        Button btnCopy = new Button("Copiar saída");
        btnCopy.getStyleClass().add("btn");
        btnCopy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(output.getText());
            Clipboard.getSystemClipboard().setContent(cc);
        });

        Button btnClear = new Button("Limpar");
        btnClear.getStyleClass().add("btn-ghost");
        btnClear.setOnAction(e -> output.setText(""));

        Button btnExport = new Button("Exportar histórico (CSV)");
        btnExport.getStyleClass().add("btn-ghost");
        btnExport.setOnAction(e -> exportHistoryCsv());

        HBox actions = new HBox(btnCopy, btnClear, btnExport);
        actions.setSpacing(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(titles, new Region(), actions);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setPadding(new Insets(14));
        header.getStyleClass().add("header");
        return header;
    }

    // ---------------- SIDEBAR ----------------
    private VBox buildSidebar(){
        Label navTitle = new Label("Ferramentas");
        navTitle.getStyleClass().add("navTitle");

        ToggleGroup group = new ToggleGroup();

        VBox nav = new VBox(
                navTitle,
                navBtn("Status", group, () -> selectTab("Status")),
                navBtn("Ping", group, () -> selectTab("Ping")),
                navBtn("DNS Lookup", group, () -> selectTab("DNS Lookup")),
                navBtn("Reverse DNS", group, () -> selectTab("Reverse DNS")),
                navBtn("Porta TCP", group, () -> selectTab("Porta TCP")),
                navBtn("HTTP/HTTPS", group, () -> selectTab("HTTP/HTTPS")),
                navBtn("SSL Cert", group, () -> selectTab("SSL Cert")),
                navBtn("Traceroute", group, () -> selectTab("Traceroute")),
                navBtn("Interfaces (Java)", group, () -> selectTab("Interfaces (Java)")),
                navBtn("Rotas (SO)", group, () -> selectTab("Rotas (SO)")),
                navBtn("ARP (SO)", group, () -> selectTab("ARP (SO)")),
                navBtn("Netstat (SO)", group, () -> selectTab("Netstat (SO)")),
                navBtn("Histórico", group, () -> selectTab("Histórico"))
        );
        nav.setPadding(new Insets(14));
        nav.setSpacing(10);
        nav.getStyleClass().add("sidebar");

        ((ToggleButton)nav.getChildren().get(1)).setSelected(true);

        VBox quick = new VBox();
        quick.getStyleClass().add("card");
        quick.setPadding(new Insets(12));
        quick.setSpacing(6);
        Label qh = new Label("Dicas rápidas (Windows)");
        qh.getStyleClass().add("h2");
        quick.getChildren().addAll(
                qh,
                tip("IP: ipconfig /all"),
                tip("Rotas: route print"),
                tip("Portas: netstat -ano"),
                tip("DNS: nslookup domínio"),
                tip("Traceroute: tracert domínio")
        );

        VBox wrap = new VBox(nav, quick);
        wrap.setSpacing(12);
        wrap.setPadding(new Insets(14, 0, 14, 14));
        wrap.setPrefWidth(300);
        return wrap;
    }

    private Label tip(String t){
        Label l = new Label("• " + t);
        l.getStyleClass().addAll("muted","mono");
        return l;
    }

    private ToggleButton navBtn(String text, ToggleGroup group, Runnable onClick){
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.getStyleClass().add("navBtn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> onClick.run());
        return b;
    }

    // ---------------- MAIN (Tabs + Output) ----------------
    private VBox buildMain(){
        tabs = new TabPane();
        tabs.getStyleClass().add("tabs");

        tabs.getTabs().addAll(
                tabStatus(),
                tabPing(),
                tabDNS(),
                tabReverseDNS(),
                tabPorta(),
                tabHttp(),
                tabSsl(),
                tabTraceroute(),
                tabInterfacesJava(),
                tabRotasSO(),
                tabArpSO(),
                tabNetstatSO(),
                tabHistorico()
        );

        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.getStyleClass().add("output");
        output.setText("Pronto. Escolha uma ferramenta e execute um teste.\n");

        VBox main = new VBox(tabs, output);
        main.setSpacing(12);
        main.setPadding(new Insets(14, 14, 14, 10));
        VBox.setVgrow(output, Priority.ALWAYS);
        return main;
    }

    private void selectTab(String name){
        for (Tab t : tabs.getTabs()){
            if (t.getText().equalsIgnoreCase(name)){
                tabs.getSelectionModel().select(t);
                return;
            }
        }
    }

    // ---------------- TABS ----------------
    private Tab tabStatus(){
        Tab tab = new Tab("Status");
        tab.setClosable(false);

        statusHost = new Label("-");
        statusIp = new Label("-");
        statusOs = new Label("-");
        statusHost.getStyleClass().add("monoStrong");
        statusIp.getStyleClass().add("monoStrong");
        statusOs.getStyleClass().add("monoStrong");

        VBox card = new VBox(
                statusRow("Hostname", statusHost),
                statusRow("IP local (heurística)", statusIp),
                statusRow("Sistema", statusOs)
        );
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));
        card.setSpacing(10);

        Button refresh = new Button("Atualizar");
        refresh.getStyleClass().add("btn-primary");
        refresh.setOnAction(e -> refreshStatus());

        Button details = new Button("Listar Interfaces (Java)");
        details.getStyleClass().add("btn-ghost");
        details.setOnAction(e -> {
            selectTab("Interfaces (Java)");
            runInterfacesJavaLogged();
        });

        HBox actions = new HBox(refresh, details);
        actions.setSpacing(10);

        VBox content = new VBox(card, actions);
        content.setPadding(new Insets(12));
        content.setSpacing(12);
        tab.setContent(content);
        return tab;
    }

    private HBox statusRow(String label, Label value){
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        HBox hb = new HBox(l, new Region(), value);
        hb.getStyleClass().add("row");
        hb.setPadding(new Insets(10));
        HBox.setHgrow(hb.getChildren().get(1), Priority.ALWAYS);
        return hb;
    }

    private Tab tabPing(){
        Tab tab = new Tab("Ping");
        tab.setClosable(false);

        TextField host = new TextField();
        host.setPromptText("Ex: 8.8.8.8 ou google.com");
        host.getStyleClass().add("input");

        Spinner<Integer> count = new Spinner<>(1, 10, 4);
        count.setEditable(true);
        count.getStyleClass().add("input");

        Button run = new Button("Executar Ping");
        run.getStyleClass().add("btn-success");
        run.setOnAction(e -> {
            String h = host.getText().trim();
            if (h.isEmpty()) { toast("Informe um host/IP."); return; }
            runCommandLogged("ping", h, pingCommand(h, count.getValue()), 30);
        });

        VBox card = formCard("Ping (ICMP) — via ferramenta do SO", List.of(
                labeled("Host/IP", host),
                labeled("Qtde", count)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabDNS(){
        Tab tab = new Tab("DNS Lookup");
        tab.setClosable(false);

        TextField host = new TextField();
        host.setPromptText("Ex: google.com");
        host.getStyleClass().add("input");

        ChoiceBox<String> mode = new ChoiceBox<>();
        mode.getItems().addAll("Java (InetAddress)", "SO (nslookup)");
        mode.setValue("Java (InetAddress)");
        mode.getStyleClass().add("input");

        Button run = new Button("Consultar DNS");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String h = host.getText().trim();
            if (h.isEmpty()) { toast("Informe um domínio."); return; }

            if ("Java (InetAddress)".equals(mode.getValue())){
                dnsJavaLogged(h);
            } else {
                runCommandLogged("dns", h, dnsCommand(h), 25);
            }
        });

        VBox card = formCard("DNS Lookup", List.of(
                labeled("Domínio", host),
                labeled("Modo", mode)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabReverseDNS(){
        Tab tab = new Tab("Reverse DNS");
        tab.setClosable(false);

        TextField ip = new TextField();
        ip.setPromptText("Ex: 8.8.8.8");
        ip.getStyleClass().add("input");

        Button run = new Button("Consultar Reverse DNS");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String v = ip.getText().trim();
            if (v.isEmpty()) { toast("Informe um IP."); return; }
            reverseDnsJavaLogged(v);
        });

        VBox card = formCard("Reverse DNS (PTR) — Java", List.of(
                labeled("IP", ip)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabPorta(){
        Tab tab = new Tab("Porta TCP");
        tab.setClosable(false);

        TextField host = new TextField();
        host.setPromptText("Ex: 1.1.1.1");
        host.getStyleClass().add("input");

        TextField port = new TextField();
        port.setPromptText("Ex: 443");
        port.getStyleClass().add("input");

        Spinner<Integer> timeout = new Spinner<>(500, 10000, 3000, 500);
        timeout.setEditable(true);
        timeout.getStyleClass().add("input");

        Button run = new Button("Testar Porta");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String h = host.getText().trim();
            String p = port.getText().trim();
            if (h.isEmpty()) { toast("Informe um host/IP."); return; }
            int pn;
            try { pn = Integer.parseInt(p); } catch(Exception ex){ toast("Porta inválida."); return; }
            testPortLogged(h, pn, timeout.getValue());
        });

        VBox card = formCard("Teste de Porta TCP — Java (Socket.connect)", List.of(
                labeled("Host/IP", host),
                labeled("Porta", port),
                labeled("Timeout (ms)", timeout)
        ), run);

        Label note = new Label("Observação: isso testa conectividade TCP (não é varredura).");
        note.getStyleClass().add("muted");

        VBox content = new VBox(card, note);
        content.setSpacing(10);
        tab.setContent(wrap(content));
        return tab;
    }

    private Tab tabHttp(){
        Tab tab = new Tab("HTTP/HTTPS");
        tab.setClosable(false);

        TextField url = new TextField();
        url.setPromptText("Ex: https://www.google.com");
        url.getStyleClass().add("input");

        Spinner<Integer> timeout = new Spinner<>(1000, 30000, 8000, 1000);
        timeout.setEditable(true);
        timeout.getStyleClass().add("input");

        Button run = new Button("Testar HTTP/HTTPS");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String u = url.getText().trim();
            if (u.isEmpty()) { toast("Informe uma URL."); return; }
            httpCheckLogged(u, timeout.getValue());
        });

        VBox card = formCard("HTTP/HTTPS — Java (HttpURLConnection)", List.of(
                labeled("URL", url),
                labeled("Timeout (ms)", timeout)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabSsl(){
        Tab tab = new Tab("SSL Cert");
        tab.setClosable(false);

        TextField host = new TextField();
        host.setPromptText("Ex: google.com");
        host.getStyleClass().add("input");

        TextField port = new TextField();
        port.setText("443");
        port.getStyleClass().add("input");

        Spinner<Integer> timeout = new Spinner<>(1000, 20000, 6000, 1000);
        timeout.setEditable(true);
        timeout.getStyleClass().add("input");

        Button run = new Button("Ler Certificado SSL");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String h = host.getText().trim();
            if (h.isEmpty()) { toast("Informe um host."); return; }
            int p;
            try { p = Integer.parseInt(port.getText().trim()); } catch(Exception ex){ toast("Porta inválida."); return; }
            sslInfoLogged(h, p, timeout.getValue());
        });

        VBox card = formCard("SSL/TLS — Java (SSLSocket)", List.of(
                labeled("Host", host),
                labeled("Porta", port),
                labeled("Timeout (ms)", timeout)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabTraceroute(){
        Tab tab = new Tab("Traceroute");
        tab.setClosable(false);

        TextField host = new TextField();
        host.setPromptText("Ex: google.com");
        host.getStyleClass().add("input");

        Spinner<Integer> hops = new Spinner<>(5, 60, 30, 5);
        hops.setEditable(true);
        hops.getStyleClass().add("input");

        Button run = new Button("Executar Traceroute");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> {
            String h = host.getText().trim();
            if (h.isEmpty()) { toast("Informe um host/domínio."); return; }
            runCommandLogged("traceroute", h, tracerouteCommand(h, hops.getValue()), 90);
        });

        VBox card = formCard("Traceroute — via ferramenta do SO", List.of(
                labeled("Destino", host),
                labeled("Máx. saltos", hops)
        ), run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabInterfacesJava(){
        Tab tab = new Tab("Interfaces (Java)");
        tab.setClosable(false);

        Button run = new Button("Listar Interfaces");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> runInterfacesJavaLogged());

        VBox card = infoCard("Interfaces de Rede — Java (NetworkInterface)",
                "Lista interfaces, IPs, MAC, flags e MTU sem depender de comandos do sistema.",
                run);

        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabRotasSO(){
        Tab tab = new Tab("Rotas (SO)");
        tab.setClosable(false);

        Button run = new Button("Mostrar rotas");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> runCommandLogged("route", "route", routesCommand(), 25));

        VBox card = infoCard("Tabela de Rotas (SO)", "Usa comando do sistema (Windows: route print).", run);
        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabArpSO(){
        Tab tab = new Tab("ARP (SO)");
        tab.setClosable(false);

        Button run = new Button("Mostrar ARP");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> runCommandLogged("arp", "arp", arpCommand(), 20));

        VBox card = infoCard("ARP Table (SO)", "Usa comando do sistema (Windows: arp -a).", run);
        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabNetstatSO(){
        Tab tab = new Tab("Netstat (SO)");
        tab.setClosable(false);

        ChoiceBox<String> mode = new ChoiceBox<>();
        mode.getItems().addAll("Escutando (LISTEN)", "Conexões (ESTABLISHED)", "Tudo");
        mode.setValue("Escutando (LISTEN)");
        mode.getStyleClass().add("input");

        Button run = new Button("Executar Netstat");
        run.getStyleClass().add("btn-primary");
        run.setOnAction(e -> runCommandLogged("netstat", mode.getValue(), netstatCommand(mode.getValue()), 25));

        VBox card = formCard("Netstat (SO)", List.of(labeled("Modo", mode)), run);
        tab.setContent(wrap(card));
        return tab;
    }

    private Tab tabHistorico(){
        Tab tab = new Tab("Histórico");
        tab.setClosable(false);

        historyAction = new ComboBox<>();
        historyAction.getItems().addAll("all","status","ping","dns","rdns","port","http","ssl","traceroute","ifaces","route","arp","netstat","verify","export");
        historyAction.setValue("all");
        historyAction.getStyleClass().add("input");

        historySearch = new TextField();
        historySearch.setPromptText("Buscar (alvo ou trecho do resultado)");
        historySearch.getStyleClass().add("input");

        historyLimit = new Spinner<>(10, 2000, 200, 50);
        historyLimit.setEditable(true);
        historyLimit.getStyleClass().add("input");

        Button load = new Button("Carregar");
        load.getStyleClass().add("btn-primary");
        load.setOnAction(e -> refreshHistory());

        HBox controls = new HBox(
                labeled("Filtro", historyAction),
                labeled("Busca", historySearch),
                labeled("Limite", historyLimit),
                load
        );
        controls.setSpacing(10);
        controls.getStyleClass().add("card");
        controls.setPadding(new Insets(12));

        historyTable = new TableView<>();
        historyTable.getStyleClass().add("table");

        TableColumn<HistRow, String> cTs = new TableColumn<>("Data (UTC)");
        cTs.setCellValueFactory(d -> d.getValue().tsProperty());
        cTs.setPrefWidth(170);

        TableColumn<HistRow, String> cAct = new TableColumn<>("Ação");
        cAct.setCellValueFactory(d -> d.getValue().actionProperty());
        cAct.setPrefWidth(110);

        TableColumn<HistRow, String> cTarget = new TableColumn<>("Alvo");
        cTarget.setCellValueFactory(d -> d.getValue().targetProperty());
        cTarget.setPrefWidth(220);

        TableColumn<HistRow, String> cRes = new TableColumn<>("Resumo (duplo clique para ver completo)");
        cRes.setCellValueFactory(d -> d.getValue().resultProperty());
        cRes.setPrefWidth(560);

        historyTable.getColumns().addAll(cTs, cAct, cTarget, cRes);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        historyTable.setRowFactory(tv -> {
            TableRow<HistRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && (!row.isEmpty())) {
                    HistRow r = row.getItem();
                    output.clear();
                    output.setText(loadFullFromHistory(r.tsProperty().get()));
                    output.positionCaret(0);
                }
            });
            return row;
        });

        VBox content = new VBox(controls, historyTable);
        content.setSpacing(12);
        content.setPadding(new Insets(0,0,12,0));
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    // ---------------- UI Helpers ----------------
    private VBox wrap(Region content){
        VBox v = new VBox(content);
        v.setPadding(new Insets(12));
        return v;
    }

    private VBox formCard(String title, List<VBox> fields, Button primary){
        Label t = new Label(title);
        t.getStyleClass().add("h2");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        int r = 0;
        for (VBox f : fields){
            grid.add(f, 0, r++);
        }

        VBox card = new VBox(t, grid, primary);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        card.setSpacing(12);
        return card;
    }

    private VBox infoCard(String title, String desc, Button primary){
        Label t = new Label(title);
        t.getStyleClass().add("h2");
        Label d = new Label(desc);
        d.getStyleClass().add("muted");
        d.setWrapText(true);

        VBox card = new VBox(t, d, primary);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));
        card.setSpacing(12);
        return card;
    }

    private VBox labeled(String label, Control control){
        Label l = new Label(label);
        l.getStyleClass().add("muted");
        VBox v = new VBox(l, control);
        v.setSpacing(6);
        return v;
    }

    private void toast(String msg){
        output.appendText("\n\n⚠️ " + msg + "\n");
        output.positionCaret(output.getLength());
    }

    // ---------------- Status / Verificação ----------------
    private void refreshStatus(){
        runInBg(() -> {
            String h = safe(() -> InetAddress.getLocalHost().getHostName(), "N/A");
            String ip = localIp();
            String os = System.getProperty("os.name") + " " + System.getProperty("os.version");

            Platform.runLater(() -> {
                statusHost.setText(h);
                statusIp.setText(ip);
                statusOs.setText(os);
            });

            String out = "[STATUS]\nHostname: " + h + "\nIP local: " + ip + "\nSistema: " + os + "\n";
            log("status", "local", out);
            Platform.runLater(() -> {
                output.clear();
                output.setText(out);
                output.positionCaret(0);
            });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private boolean commandExists(String cmd){
        try{
            ProcessBuilder pb;
            if (isWindows()){
                pb = new ProcessBuilder("cmd","/c","where " + cmd);
            } else {
                pb = new ProcessBuilder("sh","-lc","command -v " + cmd);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS);
            if (!ok) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        }catch(Exception e){
            return false;
        }
    }

    private void verifyTools(){
        StringBuilder sb = new StringBuilder();
        sb.append("[VERIFICAÇÃO]\n");

        List<String> required = new ArrayList<>();
        if (isWindows()){
            required.addAll(List.of("ping","nslookup","tracert","route","arp","netstat","ipconfig"));
        } else {
            required.addAll(List.of("ping","nslookup","traceroute","ip","arp","netstat"));
        }

        for (String c : required){
            boolean ok = commandExists(c);
            sb.append(ok ? "✅ " : "❌ ").append(c).append("\n");
        }

        sb.append("\nObs: Rotas/ARP/Netstat/Traceroute usam ferramentas do SO. Se estiver ❌, pode ser bloqueio/política.\n");
        String msg = sb.toString();

        log("verify", "local", msg);
        Platform.runLater(() -> {
            output.appendText("\n\n" + msg);
            output.positionCaret(output.getLength());
        });
        Platform.runLater(this::refreshHistory);
    }

    // ---------------- Ações (Logs + execução) ----------------
    private void runCommandLogged(String action, String target, List<String> cmd, int timeoutSeconds){
        Platform.runLater(() -> {
            output.clear();
            output.setText("Executando: " + String.join(" ", cmd) + " ...\n");
            output.positionCaret(0);
        });

        // Checagem: garante que existe ferramenta base (quando aplicável)
        String baseCmd = (cmd != null && !cmd.isEmpty()) ? cmd.get(0) : "";
        if (isWindows() && "cmd".equalsIgnoreCase(baseCmd)) baseCmd = "netstat";
        if (!baseCmd.isEmpty() && !commandExists(baseCmd)){
            final String warn = "❌ Comando não encontrado: " + baseCmd + "\n" +
                    "Verifique se a ferramenta está instalada/habilitada no Windows (ou se há bloqueio por política).";
            Platform.runLater(() -> { output.clear(); output.setText(warn); output.positionCaret(0); });
            log(action, target, warn);
            Platform.runLater(this::refreshHistory);
            return;
        }

        runInBg(() -> {
            String out = runCmd(cmd, timeoutSeconds);
            String full = "[" + action.toUpperCase() + "] " + target + "\n\n" + out;

            log(action, target, full);

            final String fullOut = full;
            Platform.runLater(() -> {
                output.clear();
                output.setText(fullOut);
                output.positionCaret(0);
            });

            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void testPortLogged(String host, int port, int timeoutMs){
        Platform.runLater(() -> {
            output.clear();
            output.setText("Testando " + host + ":" + port + " ...\n");
            output.positionCaret(0);
        });

        runInBg(() -> {
            String target = host + ":" + port;
            String msg;

            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), timeoutMs);
                msg = "✅ Consegui conectar em " + target + " (porta ABERTA / acessível).";
            } catch (SocketTimeoutException e) {
                msg = "⏱️ Timeout em " + target + " (pode estar filtrado/indisponível).";
            } catch (ConnectException e) {
                msg = "❌ Conexão recusada em " + target + " (porta FECHADA).";
            } catch (Exception e) {
                msg = "⚠️ Falha ao testar " + target + ": " + e.getMessage();
            }

            String full = "[PORT] " + target + "\n\n" + msg;
            log("port", target, full);

            final String fullOut = full;
            Platform.runLater(() -> { output.clear(); output.setText(fullOut); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void dnsJavaLogged(String host){
        Platform.runLater(() -> { output.clear(); output.setText("Resolvendo (Java): " + host + " ...\n"); });

        runInBg(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[DNS] ").append(host).append(" (Java)\n\n");
            try{
                InetAddress[] addrs = InetAddress.getAllByName(host);
                for (InetAddress a : addrs){
                    sb.append(a.getHostAddress()).append("\n");
                }
                if (addrs.length == 0) sb.append("(sem resultados)\n");
            }catch(Exception e){
                sb.append("Erro: ").append(e.getMessage()).append("\n");
            }
            String full = sb.toString();
            log("dns", host, full);
            Platform.runLater(() -> { output.clear(); output.setText(full); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void reverseDnsJavaLogged(String ip){
        Platform.runLater(() -> { output.clear(); output.setText("Consultando Reverse DNS (Java): " + ip + " ...\n"); });

        runInBg(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[RDNS] ").append(ip).append(" (Java)\n\n");
            try{
                InetAddress a = InetAddress.getByName(ip);
                sb.append("Host: ").append(a.getHostName()).append("\n");
                sb.append("Canonical: ").append(a.getCanonicalHostName()).append("\n");
            }catch(Exception e){
                sb.append("Erro: ").append(e.getMessage()).append("\n");
            }
            String full = sb.toString();
            log("rdns", ip, full);
            Platform.runLater(() -> { output.clear(); output.setText(full); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void httpCheckLogged(String urlStr, int timeoutMs){
        Platform.runLater(() -> { output.clear(); output.setText("Testando HTTP/HTTPS: " + urlStr + " ...\n"); });

        runInBg(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[HTTP] ").append(urlStr).append("\n\n");
            long start = System.currentTimeMillis();

            try{
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestProperty("User-Agent", "AssistenteRedes/1.0");

                int code = conn.getResponseCode();
                String msg = conn.getResponseMessage();
                long elapsed = System.currentTimeMillis() - start;

                sb.append("Status: ").append(code).append(" ").append(msg).append("\n");
                sb.append("Tempo: ").append(elapsed).append(" ms\n");
                sb.append("IP: ").append(resolveIp(url.getHost())).append("\n");

                // Alguns headers úteis
                sb.append("\nHeaders:\n");
                Map<String, List<String>> headers = conn.getHeaderFields();
                int shown = 0;
                for (Map.Entry<String, List<String>> e : headers.entrySet()){
                    if (e.getKey() == null) continue;
                    sb.append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append("\n");
                    if (++shown >= 12) break; // evita poluir demais
                }

            }catch(Exception e){
                sb.append("Erro: ").append(e.getMessage()).append("\n");
            }

            String full = sb.toString();
            log("http", urlStr, full);
            Platform.runLater(() -> { output.clear(); output.setText(full); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void sslInfoLogged(String host, int port, int timeoutMs){
        Platform.runLater(() -> { output.clear(); output.setText("Lendo certificado SSL: " + host + ":" + port + " ...\n"); });

        runInBg(() -> {
            String target = host + ":" + port;
            StringBuilder sb = new StringBuilder();
            sb.append("[SSL] ").append(target).append("\n\n");

            try{
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                try (SSLSocket socket = (SSLSocket) factory.createSocket()){
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    socket.setSoTimeout(timeoutMs);
                    socket.startHandshake();

                    SSLSession session = socket.getSession();
                    sb.append("Protocolo: ").append(session.getProtocol()).append("\n");
                    sb.append("Cipher: ").append(session.getCipherSuite()).append("\n\n");

                    Certificate[] certs = session.getPeerCertificates();
                    if (certs.length > 0 && certs[0] instanceof X509Certificate){
                        X509Certificate x = (X509Certificate) certs[0];
                        sb.append("Subject: ").append(x.getSubjectX500Principal()).append("\n");
                        sb.append("Issuer: ").append(x.getIssuerX500Principal()).append("\n");
                        sb.append("Validade: ").append(x.getNotBefore()).append("  ->  ").append(x.getNotAfter()).append("\n");
                        sb.append("Serial: ").append(x.getSerialNumber()).append("\n");
                    } else {
                        sb.append("(certificado não X509 ou vazio)\n");
                    }
                }
            }catch(Exception e){
                sb.append("Erro: ").append(e.getMessage()).append("\n");
            }

            String full = sb.toString();
            log("ssl", target, full);
            Platform.runLater(() -> { output.clear(); output.setText(full); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    private void runInterfacesJavaLogged(){
        Platform.runLater(() -> { output.clear(); output.setText("Listando interfaces (Java) ...\n"); });

        runInBg(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[IFACES] Interfaces (Java)\n\n");

            try{
                Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                if (nis == null){
                    sb.append("(sem interfaces)\n");
                } else {
                    while (nis.hasMoreElements()){
                        NetworkInterface ni = nis.nextElement();
                        sb.append("Nome: ").append(ni.getName()).append("  (").append(ni.getDisplayName()).append(")\n");
                        sb.append("  Up: ").append(ni.isUp()).append("  Loopback: ").append(ni.isLoopback()).append("  Virtual: ").append(ni.isVirtual()).append("\n");
                        sb.append("  MTU: ").append(ni.getMTU()).append("\n");

                        byte[] mac = ni.getHardwareAddress();
                        if (mac != null) sb.append("  MAC: ").append(formatMac(mac)).append("\n");

                        Enumeration<InetAddress> addrs = ni.getInetAddresses();
                        while (addrs.hasMoreElements()){
                            InetAddress a = addrs.nextElement();
                            sb.append("  IP: ").append(a.getHostAddress()).append("\n");
                        }
                        sb.append("\n");
                    }
                }
            }catch(Exception e){
                sb.append("Erro: ").append(e.getMessage()).append("\n");
            }

            String full = sb.toString();
            log("ifaces", "local", full);
            Platform.runLater(() -> { output.clear(); output.setText(full); output.positionCaret(0); });
            Platform.runLater(this::refreshHistory);
            return null;
        });
    }

    // ---------------- Histórico (carregar, exportar) ----------------
    private void refreshHistory(){
        if (historyTable == null) return;

        String act = historyAction.getValue();
        String q = historySearch.getText().trim().toLowerCase();
        int limit = historyLimit.getValue();

        runInBg(() -> {
            List<HistRow> rows = new ArrayList<>();
            if (Files.exists(HISTORY)){
                List<String> lines = Files.readAllLines(HISTORY, StandardCharsets.UTF_8);
                for (int i = lines.size()-1; i >= 0 && rows.size() < limit; i--){
                    Map<String,String> m = TinyJson.parse(lines.get(i));
                    if (m.isEmpty()) continue;

                    String a = m.getOrDefault("action","");
                    String t = m.getOrDefault("target","");
                    String r = m.getOrDefault("result","");

                    if (!"all".equals(act) && !act.equals(a)) continue;
                    if (!q.isEmpty() && !(t.toLowerCase().contains(q) || r.toLowerCase().contains(q))) continue;

                    rows.add(new HistRow(
                            m.getOrDefault("ts",""),
                            a,
                            t,
                            summarize(r, 140)
                    ));
                }
            }
            Platform.runLater(() -> historyTable.getItems().setAll(rows));
            return null;
        });
    }

    private String loadFullFromHistory(String ts){
        if (!Files.exists(HISTORY)) return "(histórico vazio)";
        try{
            List<String> lines = Files.readAllLines(HISTORY, StandardCharsets.UTF_8);
            for (int i = lines.size()-1; i>=0; i--){
                Map<String,String> m = TinyJson.parse(lines.get(i));
                if (ts.equals(m.get("ts"))) return m.getOrDefault("result","");
            }
        }catch(Exception ignored){}
        return "(registro não encontrado)";
    }

    private void exportHistoryCsv(){
        Platform.runLater(() -> {
            output.appendText("\n\n[EXPORT]\nGerando CSV...\n");
            output.positionCaret(output.getLength());
        });

        runInBg(() -> {
            try{
                Path csv = HISTORY_CSV.toAbsolutePath();
                List<String> lines = new ArrayList<>();
                lines.add("ts,action,target,result");

                if (Files.exists(HISTORY)){
                    for (String ln : Files.readAllLines(HISTORY, StandardCharsets.UTF_8)){
                        Map<String,String> m = TinyJson.parse(ln);
                        if (m.isEmpty()) continue;
                        lines.add(csv(m.get("ts")) + "," + csv(m.get("action")) + "," + csv(m.get("target")) + "," + csv(m.get("result")));
                    }
                }

                Files.write(csv, lines, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

                final String msg = "[EXPORT]\n✅ CSV gerado em:\n" + csv + "\n";
                log("export", csv.toString(), msg);

                Platform.runLater(() -> {
                    output.appendText("\n" + msg);
                    output.positionCaret(output.getLength());
                });
                Platform.runLater(this::refreshHistory);
            }catch(Exception e){
                final String msg = "[EXPORT]\n❌ Falha ao gerar CSV: " + e.getMessage();
                log("export", "error", msg);
                Platform.runLater(() -> {
                    output.appendText("\n" + msg + "\n");
                    output.positionCaret(output.getLength());
                });
                Platform.runLater(this::refreshHistory);
            }
            return null;
        });
    }

    // ---------------- Utils ----------------
    private void runInBg(Callable<Void> fn){
        pool.submit(() -> {
            try {
                fn.call();
            } catch(Exception e){
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String err = "Erro: " + e.getMessage() + "\n\n" + sw;
                Platform.runLater(() -> {
                    if (output != null){
                        output.clear();
                        output.setText(err);
                        output.positionCaret(0);
                    }
                });
            }
        });
    }

    private static String runCmd(List<String> cmd, int timeoutSeconds){
        try{
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok){
                p.destroyForcibly();
                return "⏱️ Timeout ao executar: " + String.join(" ", cmd);
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return out.isBlank() ? "(sem saída)" : out.trim();
        }catch(Exception e){
            return "Erro ao executar comando: " + e.getMessage();
        }
    }

    private static boolean isWindows(){
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static List<String> pingCommand(String host, int count){
        if (isWindows()) return List.of("ping","-n",String.valueOf(count),host);
        return List.of("ping","-c",String.valueOf(count),host);
    }

    private static List<String> dnsCommand(String host){
        // nslookup host
        return List.of("nslookup", host);
    }

    private static List<String> tracerouteCommand(String host, int hops){
        if (isWindows()) return List.of("tracert","-h",String.valueOf(hops), host);
        return List.of("traceroute","-m",String.valueOf(hops), host);
    }

    private static List<String> routesCommand(){
        if (isWindows()) return List.of("route","print");
        return List.of("sh","-lc","ip r || netstat -rn");
    }

    private static List<String> arpCommand(){
        if (isWindows()) return List.of("arp","-a");
        return List.of("sh","-lc","arp -a || ip neigh");
    }

    private static List<String> netstatCommand(String mode){
        if (isWindows()){
            if ("Conexões (ESTABLISHED)".equals(mode)) return List.of("cmd","/c","netstat -ano | findstr ESTABLISHED");
            if ("Escutando (LISTEN)".equals(mode)) return List.of("cmd","/c","netstat -ano | findstr LISTEN");
            return List.of("netstat","-ano");
        } else {
            if ("Conexões (ESTABLISHED)".equals(mode)) return List.of("sh","-lc","netstat -an | grep ESTABLISHED || ss -tan state established");
            if ("Escutando (LISTEN)".equals(mode)) return List.of("sh","-lc","netstat -an | grep LISTEN || ss -tuln");
            return List.of("sh","-lc","netstat -an || ss -tulpen");
        }
    }

    private static String localIp(){
        try(final DatagramSocket socket = new DatagramSocket()){
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            return socket.getLocalAddress().getHostAddress();
        }catch(Exception e){
            return "N/A";
        }
    }

    private static String resolveIp(String host){
        try{
            InetAddress a = InetAddress.getByName(host);
            return a.getHostAddress();
        }catch(Exception e){
            return "N/A";
        }
    }

    private void log(String action, String target, String result){
        Map<String,String> m = new LinkedHashMap<>();
        m.put("ts", Instant.now().toString());
        m.put("action", action);
        m.put("target", target);
        m.put("result", cut(result, 8000));
        try{
            try { Files.createDirectories(APP_DIR); } catch (Exception ignored) {}
            Files.writeString(HISTORY, TinyJson.stringify(m) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }catch(Exception ignored){}
    }

    private static String cut(String s, int n){
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String summarize(String s, int max){
        if (s == null) return "";
        s = s.replace("\r"," ").replace("\n"," ");
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String csv(String s){
        if (s == null) s = "";
        s = s.replace("\r"," ").replace("\n"," ");
        if (s.contains(",") || s.contains("\"")){
            s = "\"" + s.replace("\"","\"\"") + "\"";
        }
        return s;
    }

    private static String formatMac(byte[] mac){
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<mac.length;i++){
            sb.append(String.format("%02X", mac[i]));
            if (i < mac.length-1) sb.append(":");
        }
        return sb.toString();
    }

    private static <T> T safe(Callable<T> fn, T def){
        try { return fn.call(); } catch(Exception e){ return def; }
    }

    /**
     * Diretório padrão para armazenar arquivos do app (histórico/exports).
     * Windows: %USERPROFILE%\Documents\AssistenteRedes
     * Outros:  ~/Documents/AssistenteRedes (ou fallback para ~/.assistenteredes)
     */
    private static Path getAppDir(){
        String home = System.getProperty("user.home");
        Path docs = Paths.get(home, "Documents");
        Path dir;
        if (Files.isDirectory(docs)){
            dir = docs.resolve("AssistenteRedes");
        } else {
            dir = Paths.get(home, ".assistenteredes");
        }
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    @Override
    public void stop() { pool.shutdownNow(); }

    public static void main(String[] args){ launch(args); }
}
