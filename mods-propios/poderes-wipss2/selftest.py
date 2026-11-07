#!/usr/bin/env python3
"""Autotest de wipspowers 3.5.0 en el servidor del banco (pack completo), mundo plano aparte.
Uso: selftest.py <etiqueta>. Deja el log en resultados/<etiqueta>/server.log y el resumen en selftest.txt."""
import os, re, shutil, sys, time
sys.path.insert(0, '/home/user/wipss2-pack/tools')
import bench
label = sys.argv[1]
S = bench.SERVER
out = os.path.join(bench.OUT, label)
os.makedirs(out, exist_ok=True)
props = open(os.path.join(S, 'server.properties')).read()
orig = props
props = re.sub(r'^level-name=.*$', 'level-name=world-selftest', props, flags=re.M)
props = re.sub(r'^level-type=.*$', 'level-type=minecraft\\\\:flat', props, flags=re.M)
open(os.path.join(S, 'server.properties'), 'w').write(props)
shutil.rmtree(os.path.join(S, 'world-selftest'), ignore_errors=True)
s = bench.Server('6G', '-XX:+UseG1GC -XX:MaxGCPauseMillis=40 -Dwips.selftest=true')
try:
    s.wait_for(r'Done \(([\d.]+)s\)', 900)
    time.sleep(5)
    i = s.mark()
    s.cmd('wips selftest')
    s.wait_for(r'SELFTEST FIN', 1800, i)
    time.sleep(2)
finally:
    s.stop()
    open(os.path.join(S, 'server.properties'), 'w').write(orig)
    with open(os.path.join(out, 'server.log'), 'w') as f:
        f.write('\n'.join(s.lines))
    lines = [l for l in s.lines if 'SELFTEST' in l]
    with open(os.path.join(out, 'selftest.txt'), 'w') as f:
        f.write('\n'.join(l[l.find('SELFTEST'):] for l in lines))
    print('\n'.join(l[l.find('SELFTEST'):] for l in lines))
