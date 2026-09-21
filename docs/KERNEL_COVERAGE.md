# Kernel Coverage Matrix

Kernel NAME does not matter - only X.Y.Z numbers do. Every version below
is loadable: EXACT module when bundled, otherwise nearest-series module +
automatic vermagic patch + force-load ladder.

**Bundled modules:** 4.9.337, 4.9.307, 4.9.186, 4.14.117, 4.14.180, 4.14.186, 4.19.81, 4.19.113, 4.19.157, 4.19.191, 5.4.61, 5.4.191, 5.10.198, 5.15.167, 6.1.112, 6.6.57, 3.10.108, 3.11.10, 3.12.74, 3.13.11, 3.14.79, 3.15.10, 3.16.85, 3.17.8, 3.18.140, 3.19.8, 3.7.10, 3.8.13, 3.9.11, 4.0.9, 4.1.52, 4.2.8, 4.3.6

## Linux 3.x

| Kernel version | Bundled module used | Mode |
|---|---|---|
| 3.0.101 | 3.7.10 | patch + force-load |
| 3.1.10 | 3.7.10 | patch + force-load |
| 3.2.102 | 3.7.10 | patch + force-load |
| 3.3.8 | 3.7.10 | patch + force-load |
| 3.4.113 | 3.7.10 | patch + force-load |
| 3.5.7 | 3.7.10 | patch + force-load |
| 3.6.11 | 3.7.10 | patch + force-load |
| 3.7.10 | 3.7.10 | **EXACT** |
| 3.8.13 | 3.8.13 | **EXACT** |
| 3.9.11 | 3.9.11 | **EXACT** |
| 3.10.108 | 3.10.108 | **EXACT** |
| 3.11.10 | 3.11.10 | **EXACT** |
| 3.12.74 | 3.12.74 | **EXACT** |
| 3.13.11 | 3.13.11 | **EXACT** |
| 3.14.79 | 3.14.79 | **EXACT** |
| 3.15.10 | 3.15.10 | **EXACT** |
| 3.16.85 | 3.16.85 | **EXACT** |
| 3.17.8 | 3.17.8 | **EXACT** |
| 3.18.140 | 3.18.140 | **EXACT** |
| 3.19.8 | 3.19.8 | **EXACT** |

## Linux 4.x

| Kernel version | Bundled module used | Mode |
|---|---|---|
| 4.0.9 | 4.0.9 | **EXACT** |
| 4.1.52 | 4.1.52 | **EXACT** |
| 4.2.8 | 4.2.8 | **EXACT** |
| 4.3.6 | 4.3.6 | **EXACT** |
| 4.4.302 | 4.3.6 | patch + force-load |
| 4.5.7 | 4.3.6 | patch + force-load |
| 4.6.7 | 4.3.6 | patch + force-load |
| 4.7.10 | 4.9.186 | patch + force-load |
| 4.8.17 | 4.9.186 | patch + force-load |
| 4.9.337 | 4.9.337 | **EXACT** |
| 4.10.17 | 4.9.186 | patch + force-load |
| 4.11.12 | 4.9.186 | patch + force-load |
| 4.12.14 | 4.14.117 | patch + force-load |
| 4.13.16 | 4.14.117 | patch + force-load |
| 4.14.336 | 4.14.186 | patch + force-load |
| 4.15.18 | 4.14.117 | patch + force-load |
| 4.16.18 | 4.14.117 | patch + force-load |
| 4.17.19 | 4.19.81 | patch + force-load |
| 4.18.20 | 4.19.81 | patch + force-load |
| 4.19.127 | 4.19.113 | patch + force-load |
| 4.19.325 | 4.19.191 | patch + force-load |
| 4.20.17 | 4.19.81 | patch + force-load |

## Linux 5.x

| Kernel version | Bundled module used | Mode |
|---|---|---|
| 5.0.21 | 5.4.61 | patch + force-load |
| 5.1.21 | 5.4.61 | patch + force-load |
| 5.2.20 | 5.4.61 | patch + force-load |
| 5.3.18 | 5.4.61 | patch + force-load |
| 5.4.284 | 5.4.191 | patch + force-load |
| 5.5.19 | 5.4.61 | patch + force-load |
| 5.6.19 | 5.4.61 | patch + force-load |
| 5.7.19 | 5.4.61 | patch + force-load |
| 5.8.18 | 5.10.198 | patch + force-load |
| 5.9.16 | 5.10.198 | patch + force-load |
| 5.10.226 | 5.10.198 | patch + force-load |
| 5.11.22 | 5.10.198 | patch + force-load |
| 5.12.19 | 5.10.198 | patch + force-load |
| 5.13.19 | 5.15.167 | patch + force-load |
| 5.14.21 | 5.15.167 | patch + force-load |
| 5.15.167 | 5.15.167 | **EXACT** |
| 5.16.20 | 5.15.167 | patch + force-load |
| 5.17.15 | 5.15.167 | patch + force-load |
| 5.18.19 | 5.15.167 | patch + force-load |
| 5.19.17 | 5.15.167 | patch + force-load |

## Linux 6.x

| Kernel version | Bundled module used | Mode |
|---|---|---|
| 6.0.19 | 6.1.112 | patch + force-load |
| 6.1.110 | 6.1.112 | patch + force-load |
| 6.2.16 | 6.1.112 | patch + force-load |
| 6.3.13 | 6.1.112 | patch + force-load |
| 6.4.16 | 6.6.57 | patch + force-load |
| 6.5.13 | 6.6.57 | patch + force-load |
| 6.6.52 | 6.6.57 | patch + force-load |
| 6.7.12 | 6.6.57 | patch + force-load |
| 6.8.12 | 6.6.57 | patch + force-load |
| 6.9.12 | 6.6.57 | patch + force-load |
| 6.10.14 | 6.6.57 | patch + force-load |
| 6.11.11 | 6.6.57 | patch + force-load |
| 6.12.18 | 6.6.57 | patch + force-load |
| 6.13.10 | 6.6.57 | patch + force-load |
| 6.14.8 | 6.6.57 | patch + force-load |
| 6.15.5 | 6.6.57 | patch + force-load |
| 6.16.4 | 6.6.57 | patch + force-load |
| 6.17.3 | 6.6.57 | patch + force-load |
| 6.18.2 | 6.6.57 | patch + force-load |
| 6.19.1 | 6.6.57 | patch + force-load |

## Linux 7.x

| Kernel version | Bundled module used | Mode |
|---|---|---|
| 7.0.15 | 6.1.112 | patch + force-load |
| 7.1.10 | 6.1.112 | patch + force-load |
| 7.2.4 | 6.1.112 | patch + force-load |
