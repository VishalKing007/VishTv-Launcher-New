#!/bin/bash
# Vishie TV build helper.
# Is sandbox me Gradle ko do cheezein chahiye:
#  1) Egress proxy — $https_proxy se padha jata hai, kahin likha nahi jata.
#  2) JDK ka apna cacerts trust store — workspace wala truststore.p12
#     adhoora hai aur dl.google.com ki chain usse verify nahi hoti.
set -e
source "$HOME/workspace/tools/env.sh"
# Client JVM ko IPv4 par rakho: daemon [::ffff:127.0.0.1] par sunta hai aur
# IPv6-first client usse "message nahi mila" kehkar haar maan leta hai.
# (Purana build.sh bhi yahi karta tha — yahi asal fix hai.)
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
# Gradle ka ghar workspace me rakho — /root wala cache VM badalte hi udd
# jata hai (2026-10-28 ko yahi hua: AGP cache gayab, build fail).
export GRADLE_USER_HOME="$HOME/workspace/tools/gradle-home"
read PH PP PU PW < <(python3 -c "
import urllib.parse, os
u = urllib.parse.urlparse(os.environ['https_proxy'])
print(u.hostname, u.port, u.username, u.password)")
cd "$HOME/workspace/tv-launcher"
exec gradle "$@" \
  -Dhttp.proxyHost="$PH" -Dhttp.proxyPort="$PP" \
  -Dhttp.proxyUser="$PU" -Dhttp.proxyPassword="$PW" \
  -Dhttps.proxyHost="$PH" -Dhttps.proxyPort="$PP" \
  -Dhttps.proxyUser="$PU" -Dhttps.proxyPassword="$PW" \
  -Dhttp.nonProxyHosts=localhost -Dhttps.nonProxyHosts=localhost \
  -Djavax.net.ssl.trustStore="/home/hatch/workspace/tools/gradle-cacerts.jks" \
  -Djavax.net.ssl.trustStorePassword=changeit \
  -Djavax.net.ssl.trustStoreType=JKS \
  -Djava.net.preferIPv4Stack=true
