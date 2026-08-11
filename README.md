<p align="center"><img src="docs/banner.svg" alt="EquivalencyTech banner" width="100%"></p>

# EquivalencyTech

EMC-based matter transmutation for the DrakesCraft Slimefun ecosystem. Players learn item
values, store EMC and convert resources through a deliberate progression path.

## DrakesCraft edition

- Targets Java 21 and Paper/Purpur 1.21.11.
- Compiles against the `com.github.drakescraft_labs.slimefun4` compatibility API.
- Preserves the addon's original package structure and gameplay model.
- Uses maintained dependency repositories and reproducible Maven builds.

## Building

```bash
mvn -B -ntp clean package
```

The deployable artifact is produced in `target/`. It requires
[`Slimefun4-Drake`](https://github.com/DrakesCraft-Labs/Slimefun4-Drake) at runtime.

## Provenance

Integrated from [SlimefunGuguProject/EquivalencyTech](https://github.com/SlimefunGuguProject/EquivalencyTech).
Original authorship and the GPL-3.0 license are preserved. DrakesCraft-Labs maintains only the
1.21.11 compatibility layer and server integration.
