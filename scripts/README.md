### Commands 

#### knit_to_mermaid.py
```python scripts/knit_to_mermaid.py demo-jvm/build/knit.json -o demo-jvm/build/knit.mmd```

This runs a script to directly convert ```knit.json``` into a mermaid diagram. 

#### adjacency_list_test
```python -m unittest adjacency_list_test```

This tests the AdjacencyList class which builds an adjacency list based on ```knit.json```

### visualiser_test.py
```python -m unittest visualiser_test.py```

This tests the Visualiser class which generates a Mermaid script based on the AdjacencyList built.