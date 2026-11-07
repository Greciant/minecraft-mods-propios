# Argumentos de Java para el cliente (CurseForge, PC con 16 GB)

**CurseForge → instancia WIP SSJ2 troll → ⋯ → Perfil → Memoria asignada:** `8192` MB.
El servidor medido usa 1,3 GB de heap vivo; el cliente suma modelos, texturas y el servidor integrado. Con 8 GB queda margen sin quitarle la mitad de la RAM a Windows y al driver de la GPU. Más heap no da más FPS: solo hace los GC más largos.

**Argumentos adicionales de JVM** (en el mismo perfil):
```
-XX:+UseG1GC -XX:MaxGCPauseMillis=40 -XX:+ParallelRefProcEnabled -XX:G1HeapRegionSize=16M -XX:+UseStringDeduplication
```
Medido en el servidor de pruebas contra G1 por defecto (mediana de 3, misma máquina):
- pausa de GC máxima: 101 frente a 165 ms;
- MSPT p95: 5,8 frente a 7,2 ms;
- peor tick: 80 frente a 135 ms.

Las pausas de GC largas son las que en el cliente se sienten como microtirones.

**Opción B, solo si la A no te convence en la prueba de FPS:** ZGC generacional.
```
-XX:+UseZGC -XX:+ZGenerational
```
En el servidor dio la misma media que G1 ajustado y un peor tick algo menor (46 frente a 79 ms). Pero usa más RAM fuera del heap, así que con 16 GB conviene comprobarlo con la planilla de FPS antes de quedárselo.

No uses flags "experimentales" ni `-Xmn`/`NewRatio` sueltos: no hacen falta y pueden desestabilizar.
