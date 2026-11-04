# Golpes de pose (cortes secos + estallido de aura)

Pedido del usuario (2026-10-30): "la idea es que el cambio entre poses sea brusco y que cada que hagas una pose emitas particulas de aura".

## Lo que hace el video (ref/referencia.mp4)
Desde el doble biceps, cada cambio de pose es un corte de 1-2 fotogramas con una explosion de chispas azules:
corte a perfil 7,43 s; a un brazo arriba 9,80 s; al dedo 12,67 s (camara pegada); a timido musculoso 13,97 s (camara abierta);
a palito 14,97 s. El video corta unos 0,12 s antes del golpe fuerte de la musica; en el juego conviene caer sobre el golpe.

## Golpes de la musica (aura_chad.ogg = aura_chad_mortals.ogg, md5 006812fd...)
Pulso 0,3195 s (187,8 BPM), fase 0,022 s. Golpes fuertes (flujo espectral >= 5x mediana):
5,13 7,69 9,93 10,24 12,81 14,08 14,72 15,04 15,36 17,91

## Tabla de golpes (t en s desde la activacion)
| # | t     | pose                                   | codo D/I | camara              | fuerza |
|---|-------|----------------------------------------|----------|---------------------|--------|
| 0 | 0.00  | SHY (quieto, sin SHY2 ni RELAX)        | 25/25    | 1.0                 | 0.5    |
| 1 | 3.86  | UPV                                    | 5/65     | 1.0                 | 0.7    |
| 2 | 4.50  | DOUBLE delgado (pecho 1.0, brazos 1.0) | 100/100  | 1.0                 | 0.8    |
| 3 | 5.13  | DOUBLE musculoso (pecho 1.12) = drop   | 110/110  | 1.0, acercamiento suave a 0.8 en 7.6 | 1.5 |
| 4 | 7.69  | SIDE                                   | 95/95    | 0.8                 | 1.0    |
| 5 | 9.93  | ONEUP                                  | 110/10   | 1.0 (corte)         | 1.0    |
| 6 | 12.81 | FINGER                                 | 120/10   | 0.6 (corte)         | 1.2    |
| 7 | 14.08 | SHYMUS                                 | 30/30    | 1.0 (corte)         | 1.2    |
| 8 | 15.04 | THIN                                   | 12/12    | 1.0                 | 0.7    |
| 9 | 17.91 | TURN (giro seco) y caminata hasta 21.8 | 12/12 + rodillas al paso | 1.0  | 0.5    |

Entre golpes la pose queda quieta (meseta); el cambio es instantaneo. Lo unico continuo: el acercamiento de camara 5.13-7.6 y la caminata final.
Objetivos de antebrazo: SHY/SHYMUS T_SHY, UPV T_UPV_R/L, DOUBLE delgado T_DBL, musculoso T_MUS, SIDE T_SIDE, ONEUP T_ONE_R/L, FINGER T_FNG_R/L, THIN/TURN sin objetivo.
