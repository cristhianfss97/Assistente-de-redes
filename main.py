import threading
import customtkinter as ctk
from datetime import datetime

import network_tools as net

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("dark-blue")


class App(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Assistente de Redes • PRO (Desktop)")
        self.geometry("1100x720")
        self.minsize(980, 660)

        self.grid_columnconfigure(0, weight=0)
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        self.sidebar = ctk.CTkFrame(self, corner_radius=16)
        self.sidebar.grid(row=0, column=0, sticky="nsw", padx=14, pady=14)
        self.sidebar.grid_rowconfigure(10, weight=1)

        title = ctk.CTkLabel(self.sidebar, text="🛰️ Assistente de Redes", font=ctk.CTkFont(size=18, weight="bold"))
        title.grid(row=0, column=0, padx=14, pady=(14, 0), sticky="w")

        sub = ctk.CTkLabel(
            self.sidebar,
            text="Interface nativa (sem pythonnet)\nmais compatível em outras máquinas.",
            font=ctk.CTkFont(size=12),
            text_color="#A9B6D6",
            justify="left",
        )
        sub.grid(row=1, column=0, padx=14, pady=(6, 14), sticky="w")

        self.host_var = ctk.StringVar(value="8.8.8.8")
        self.port_var = ctk.StringVar(value="443")

        ctk.CTkLabel(self.sidebar, text="Host / IP / URL", text_color="#A9B6D6").grid(row=2, column=0, padx=14, pady=(0, 6), sticky="w")
        self.host_entry = ctk.CTkEntry(self.sidebar, textvariable=self.host_var, width=320)
        self.host_entry.grid(row=3, column=0, padx=14, pady=(0, 12), sticky="w")

        ctk.CTkLabel(self.sidebar, text="Porta", text_color="#A9B6D6").grid(row=4, column=0, padx=14, pady=(0, 6), sticky="w")
        self.port_entry = ctk.CTkEntry(self.sidebar, textvariable=self.port_var, width=120)
        self.port_entry.grid(row=5, column=0, padx=14, pady=(0, 14), sticky="w")

        self.btn_ips = ctk.CTkButton(self.sidebar, text="📡 IPs (Local/Público)", command=self.run_ips)
        self.btn_ips.grid(row=6, column=0, padx=14, pady=(0, 10), sticky="ew")

        row_tools = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        row_tools.grid(row=7, column=0, padx=14, pady=(0, 10), sticky="ew")
        row_tools.grid_columnconfigure(0, weight=1)
        row_tools.grid_columnconfigure(1, weight=1)

        ctk.CTkButton(row_tools, text="🧪 Ping", command=self.run_ping).grid(row=0, column=0, padx=(0, 6), pady=0, sticky="ew")
        ctk.CTkButton(row_tools, text="🧭 Traceroute", command=self.run_trace).grid(row=0, column=1, padx=(6, 0), pady=0, sticky="ew")

        row_tools2 = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        row_tools2.grid(row=8, column=0, padx=14, pady=(0, 12), sticky="ew")
        row_tools2.grid_columnconfigure(0, weight=1)
        row_tools2.grid_columnconfigure(1, weight=1)

        ctk.CTkButton(row_tools2, text="🔎 DNS → IP", command=self.run_dns).grid(row=0, column=0, padx=(0, 6), pady=0, sticky="ew")
        ctk.CTkButton(row_tools2, text="🚪 Scan Porta", command=self.run_scan).grid(row=0, column=1, padx=(6, 0), pady=0, sticky="ew")

        ctk.CTkLabel(self.sidebar, text="Ferramentas avançadas", text_color="#A9B6D6").grid(row=9, column=0, padx=14, pady=(4, 8), sticky="w")
        adv = ctk.CTkScrollableFrame(self.sidebar, height=210, corner_radius=14)
        adv.grid(row=10, column=0, padx=14, pady=(0, 12), sticky="nsew")

        def adv_btn(text, cmd):
            b = ctk.CTkButton(adv, text=text, command=cmd)
            b.pack(fill="x", pady=6)

        adv_btn("🧾 IPConfig /all", self.run_ipconfig)
        adv_btn("🧹 Flush DNS", self.run_flush)
        adv_btn("🔁 Release/Renew IP", self.run_renew)
        adv_btn("🧩 Adaptadores", self.run_adapters)
        adv_btn("📒 ARP -a", self.run_arp)
        adv_btn("🔗 Netstat -ano", self.run_netstat)
        adv_btn("🗺️ Route print", self.run_route)
        adv_btn("📊 Uso de rede", self.run_usage)
        adv_btn("🧽 Limpar console", self.clear_console)

        self.ip_local_lbl = ctk.CTkLabel(self.sidebar, text="IP Local: —", font=ctk.CTkFont(size=12), text_color="#A9B6D6")
        self.ip_local_lbl.grid(row=11, column=0, padx=14, pady=(0, 4), sticky="w")
        self.ip_public_lbl = ctk.CTkLabel(self.sidebar, text="IP Público: —", font=ctk.CTkFont(size=12), text_color="#A9B6D6")
        self.ip_public_lbl.grid(row=12, column=0, padx=14, pady=(0, 14), sticky="w")

        self.main = ctk.CTkFrame(self, corner_radius=16)
        self.main.grid(row=0, column=1, sticky="nsew", padx=(0, 14), pady=14)
        self.main.grid_rowconfigure(2, weight=1)
        self.main.grid_columnconfigure(0, weight=1)

        top = ctk.CTkFrame(self.main, corner_radius=16)
        top.grid(row=0, column=0, sticky="ew", padx=14, pady=(14, 8))
        top.grid_columnconfigure(0, weight=1)

        self.status_lbl = ctk.CTkLabel(top, text="✅ Pronto", font=ctk.CTkFont(size=12), text_color="#A9B6D6")
        self.status_lbl.grid(row=0, column=0, sticky="w", padx=14, pady=10)

        self.console = ctk.CTkTextbox(self.main, wrap="word", corner_radius=14)
        self.console.grid(row=2, column=0, sticky="nsew", padx=14, pady=(0, 14))
        self.console.configure(font=("Consolas", 11))
        self.log("Assistente de Redes iniciado. Dica: use '🔎 DNS → IP' para colar um https:// e resolver IP.")

        self.host_entry.bind("<Return>", lambda e: self.run_ping())

        self.run_ips()

    def set_status(self, text):
        self.status_lbl.configure(text=text)

    def log(self, msg):
        ts = datetime.now().strftime("%H:%M:%S")
        self.console.insert("end", f"[{ts}] {msg}\n")
        self.console.see("end")

    def clear_console(self):
        self.console.delete("1.0", "end")
        self.set_status("✅ Pronto")

    def _run_bg(self, title, fn):
        host = self.host_var.get().strip()
        port = self.port_var.get().strip()

        def worker():
            try:
                self.set_status(f"⏳ {title}...")
                out = fn(host, port)
                self.log(f"--- {title} ---\n{out}\n")
                self.set_status("✅ Concluído")
            except Exception as e:
                self.log(f"--- {title} (ERRO) ---\n{e}\n")
                self.set_status("❌ Erro")
        threading.Thread(target=worker, daemon=True).start()

    def run_ips(self):
        def worker():
            self.set_status("⏳ Buscando IPs...")
            local = net.get_local_ip()
            public = net.get_public_ip()
            self.ip_local_lbl.configure(text=f"IP Local: {local}")
            self.ip_public_lbl.configure(text=f"IP Público: {public}")
            self.log(f"--- IPs ---\nIP Local: {local}\nIP Público: {public}\n")
            self.set_status("✅ IPs atualizados")
        threading.Thread(target=worker, daemon=True).start()

    def run_ping(self):
        self._run_bg("Ping", lambda h, p: net.ping(h))

    def run_trace(self):
        self._run_bg("Traceroute", lambda h, p: net.traceroute(h))

    def run_scan(self):
        self._run_bg("Scan de Porta", lambda h, p: net.scan_port(h, p))

    def run_dns(self):
        self._run_bg("DNS Resolve (URL → IP)", lambda h, p: net.dns_resolve(h))

    def run_ipconfig(self):
        self._run_bg("IPConfig /all", lambda h, p: net.ipconfig_all())

    def run_flush(self):
        self._run_bg("Flush DNS", lambda h, p: net.flush_dns())

    def run_renew(self):
        self._run_bg("Release/Renew", lambda h, p: net.release_renew())

    def run_arp(self):
        self._run_bg("ARP -a", lambda h, p: net.arp_table())

    def run_netstat(self):
        self._run_bg("Netstat -ano", lambda h, p: net.netstat())

    def run_route(self):
        self._run_bg("Route print", lambda h, p: net.route_print())

    def run_adapters(self):
        self._run_bg("Adaptadores", lambda h, p: net.adapters())

    def run_usage(self):
        self._run_bg("Uso de rede", lambda h, p: net.usage())


if __name__ == "__main__":
    app = App()
    app.mainloop()
