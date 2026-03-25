import socket
import subprocess
from urllib.parse import urlparse
import psutil
import requests

def _run(cmd):
    """Run a command and return stdout+stderr (Windows friendly)."""
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, shell=False)
        out = (p.stdout or "") + (("\n" + p.stderr) if p.stderr else "")
        return out.strip() or "(sem saída)"
    except Exception as e:
        return f"Erro ao executar {cmd}: {e}"

def get_local_ip():
    try:
        return socket.gethostbyname(socket.gethostname())
    except Exception:
        return "N/A"

def get_public_ip():
    try:
        return requests.get("https://api.ipify.org", timeout=6).text.strip()
    except Exception:
        return "N/A"

def ping(host: str) -> str:
    host = (host or "").strip()
    if not host:
        return "Informe um Host/IP."
    return _run(["ping", "-n", "4", host])

def traceroute(host: str) -> str:
    host = (host or "").strip()
    if not host:
        return "Informe um Host/IP."
    return _run(["tracert", host])

def scan_port(host: str, port) -> str:
    host = (host or "").strip()
    if not host:
        return "Informe um Host/IP."
    try:
        port = int(port)
        if port < 1 or port > 65535:
            return "Porta inválida (1-65535)."
    except Exception:
        return "Porta inválida."

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(1.5)
    try:
        r = s.connect_ex((host, port))
    finally:
        try: s.close()
        except Exception: pass
    return f"Porta {port} ABERTA ✅" if r == 0 else f"Porta {port} FECHADA ❌"

def dns_resolve(value: str) -> str:
    s = (value or "").strip()
    if not s:
        return "Informe um domínio ou URL (ex: https://google.com)."

    host = None
    if "://" in s:
        try:
            host = urlparse(s).hostname
        except Exception:
            host = None
    else:
        host = s.split("/")[0]

    if not host:
        return "Não foi possível extrair o host da URL."

    out = [f"Host: {host}"]

    # socket resolve
    try:
        infos = socket.getaddrinfo(host, None)
        ips = sorted({i[4][0] for i in infos})
        if ips:
            out.append("IPs encontrados:")
            out += [f" - {ip}" for ip in ips]
        else:
            out.append("Nenhum IP retornado.")
    except Exception as e:
        out.append(f"Erro ao resolver via socket: {e}")

    # nslookup (Windows)
    try:
        ns = _run(["nslookup", host])
        if ns.strip():
            out.append("\n--- nslookup ---\n" + ns.strip())
    except Exception as e:
        out.append(f"Erro no nslookup: {e}")

    return "\n".join(out)

def ipconfig_all() -> str:
    return _run(["ipconfig", "/all"])

def flush_dns() -> str:
    return _run(["ipconfig", "/flushdns"])

def release_renew() -> str:
    a = _run(["ipconfig", "/release"])
    b = _run(["ipconfig", "/renew"])
    return a + "\n\n---\n\n" + b

def arp_table() -> str:
    return _run(["arp", "-a"])

def netstat() -> str:
    return _run(["netstat", "-ano"])

def route_print() -> str:
    return _run(["route", "print"])

def adapters() -> str:
    try:
        addrs = psutil.net_if_addrs()
        stats = psutil.net_if_stats()
        lines = []
        for name in sorted(addrs.keys()):
            st = stats.get(name)
            lines.append(f"Interface: {name}")
            if st:
                lines.append(f"  Status: {'UP' if st.isup else 'DOWN'} | Velocidade: {st.speed} Mbps | MTU: {st.mtu}")
            for a in addrs[name]:
                lines.append(f"  {a.family}: {a.address}  (mask: {a.netmask})")
            lines.append("")
        return "\n".join(lines).strip() or "(sem dados)"
    except Exception as e:
        return f"Erro ao listar adaptadores: {e}"

def usage() -> str:
    try:
        data = psutil.net_io_counters()
        return (
            f"Bytes enviados: {data.bytes_sent}\n"
            f"Bytes recebidos: {data.bytes_recv}\n"
            f"Pacotes enviados: {data.packets_sent}\n"
            f"Pacotes recebidos: {data.packets_recv}\n"
        )
    except Exception as e:
        return f"Erro ao ler uso de rede: {e}"
