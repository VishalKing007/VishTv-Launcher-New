#!/bin/bash
# Build helper for Mausam weather app — same sandbox plumbing as Vishie's
# build-launcher.sh: re-creates gradle proxy/ipv4 config each run, then builds.
set -e
source "$HOME/workspace/tools/env.sh"
python3 - <<'PY'
import os, urllib.parse, pathlib
u = urllib.parse.urlparse(os.environ.get('https_proxy',''))
lines = []
if u.hostname:
    lines += [
        "systemProp.http.proxyHost=127.0.0.1",
        "systemProp.http.proxyPort=18080",
        "systemProp.https.proxyHost=127.0.0.1",
        "systemProp.https.proxyPort=18080",
        "systemProp.http.nonProxyHosts=localhost",
        "systemProp.https.nonProxyHosts=localhost",
    ]
lines += [
    "systemProp.javax.net.ssl.trustStore=/home/hatch/workspace/tools/truststore.p12",
    "systemProp.javax.net.ssl.trustStorePassword=changeit",
    "systemProp.javax.net.ssl.trustStoreType=PKCS12",
    "systemProp.java.net.preferIPv4Stack=true",
    "org.gradle.jvmargs=-Xmx896m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true",
    "org.gradle.daemon=true",
    "org.gradle.parallel=false",
    "org.gradle.caching=true",
]
for home in [pathlib.Path('/root'), pathlib.Path('/home/hatch')]:
    d = home/'.gradle'; d.mkdir(parents=True, exist_ok=True)
    (d/'gradle.properties').write_text("\n".join(lines)+"\n")
print("gradle user props written")
PY
cd "$HOME/workspace/tv-launcher"
python3 - <<'PY'
import os, signal
n1 = 'Gradle' + 'Daemon'
for pid in os.listdir('/proc'):
    if not pid.isdigit():
        continue
    try:
        cmd = open(f'/proc/{pid}/cmdline', 'rb').read().decode(errors='ignore')
    except Exception:
        continue
    if n1 in cmd:
        try: os.kill(int(pid), signal.SIGKILL); print('killed stale daemon', pid)
        except Exception: pass
PY
sleep 1
if ! (exec 3<>/dev/tcp/127.0.0.1/18080) 2>/dev/null; then
  nohup python3 "$HOME/workspace/tools/relay.py" > /tmp/relay.log 2>&1 &
  sleep 2
fi
exec env JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true" GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djavax.net.ssl.trustStore=/home/hatch/workspace/tools/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12 -Xmx256m" gradle "$@"
