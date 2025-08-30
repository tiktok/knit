### Commands

#### knit_to_mermaid.py

`python scripts/knit_to_mermaid.py demo-jvm/build/knit.json -o demo-jvm/build/knit.mmd`

This runs a script to directly convert `knit.json` into a mermaid diagram.

#### Run tests

- Run the entire scripts test suite from the repo root:

```bash
python3 -m unittest discover -s scripts -p "*_test.py" -q
```

- Run a single test module:

```bash
python3 -m unittest scripts.adjacency_list_test -q
python3 -m unittest scripts.visualiser_test -q
python3 -m unittest scripts.validate_adjacency_test -q
```

#### Validate adjacency vs knit.json

Validate that the adjacency list built from knit.json matches knit.json itself:

```bash
python3 -m scripts.validate_adjacency_vs_knit demo-jvm/build/knit.json
```

#### Live diagram updater + change producer (optional)

In one terminal, start the diagram updater (writes `demo-jvm/build/knit_diagram.mmd`):

```bash
python3 -m scripts.diagram_updater
```

In another terminal, start the Gradle change producer in continuous mode:

```bash
./gradlew :demo-jvm:knitDump --continuous
```

As you edit Kotlin files in `demo-jvm/src/main/kotlin`, the plugin emits change files under `demo-jvm/build/changes`, and the updater applies them and refreshes the Mermaid diagram.
