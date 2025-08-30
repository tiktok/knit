import os
import json
import time
from threading import Thread

from scripts.adjacency_list import AdjacencyList
from scripts.visualiser import Visualiser
from scripts.incremental_adjacency import IncrementalAdjacency


class DiagramUpdater:
    def __init__(self, json_file_path, output_file_path, direction="TD", poll_interval=2):
        self.json_file_path = json_file_path
        self.output_file_path = output_file_path
        self.direction = direction
        self.poll_interval = poll_interval
        self.last_modified_time = None
        self.changes_dir = None
        self.last_change_seen = None
        self.running = False
        self.adjacency_list = AdjacencyList()
        self.visualiser = Visualiser()
        self.incremental = IncrementalAdjacency()
        self.current_adj = None  # cached adjacency list dict

    def check_for_changes(self):
        try:
            current_modified_time = os.path.getmtime(self.json_file_path)
            if self.last_modified_time is None or current_modified_time > self.last_modified_time:
                self.last_modified_time = current_modified_time
                return True
        except FileNotFoundError:
            print(f"ERROR: JSON file not found: {self.json_file_path}")
        return False

    def update_diagram_full(self):
        try:
            with open(self.json_file_path, 'r') as f:
                component_dumps = json.load(f)
            adjacency_list = self.adjacency_list.build_adjacency_list(component_dumps)
            self.current_adj = adjacency_list
            # Mark full build as last update for all nodes
            self.adjacency_list.reset_last_update_flags()
            self.adjacency_list.mark_full_build_update()
            mermaid_content = self.visualiser.build_mermaid_diagram(adjacency_list, self.direction)
            os.makedirs(os.path.dirname(self.output_file_path), exist_ok=True)
            with open(self.output_file_path, 'w') as f:
                f.write(mermaid_content)
            print(f"Updated Mermaid diagram at: {self.output_file_path}")
        except Exception as e:
            print(f"ERROR: Failed to update Mermaid diagram: {str(e)}")

    def find_changes_dir(self):
        base_dir = os.path.dirname(self.json_file_path)
        changes_dir = os.path.join(base_dir, "changes")
        self.changes_dir = changes_dir if os.path.isdir(changes_dir) else None

    def latest_change_file(self):
        if not self.changes_dir:
            return None
        latest_txt = os.path.join(self.changes_dir, "latest.txt")
        if not os.path.exists(latest_txt):
            return None
        try:
            with open(latest_txt, 'r') as f:
                name = f.read().strip()
            path = os.path.join(self.changes_dir, name)
            return path if os.path.exists(path) else None
        except Exception:
            return None

    def check_for_change_files(self):
        path = self.latest_change_file()
        if not path:
            return None
        if path != self.last_change_seen:
            self.last_change_seen = path
            return path
        return None

    def apply_incremental_if_any(self):
        if self.current_adj is None:
            return False
        change_file = self.check_for_change_files()
        if not change_file:
            return False
        try:
            with open(change_file, 'r') as f:
                change_data = json.load(f)
            # Update node status metadata and mark last update flags
            self.adjacency_list.reset_last_update_flags()
            self.adjacency_list.apply_status_change(change_data)
            self.current_adj = self.incremental.apply_change(self.current_adj, change_data)
            mermaid_content = self.visualiser.build_mermaid_diagram(self.current_adj, self.direction)
            os.makedirs(os.path.dirname(self.output_file_path), exist_ok=True)
            with open(self.output_file_path, 'w') as f:
                f.write(mermaid_content)
            # small summary for debugging
            node_count = len(self.current_adj)
            edge_count = sum(len(v) for v in self.current_adj.values())
            print(f"Applied change {os.path.basename(change_file)} -> nodes={node_count}, edges={edge_count}; updated diagram.")
            return True
        except Exception as e:
            print(f"ERROR: Failed to apply change file: {e}")
            return False

    def start(self):
        if self.running:
            print("DiagramUpdater is already running.")
            return
        self.running = True
        print(f"Starting DiagramUpdater for {self.json_file_path}...")
        self.find_changes_dir()
        self.update_diagram_full()

        def run():
            while self.running:
                applied = self.apply_incremental_if_any()
                if self.check_for_changes() and not applied:
                    print(f"Changes detected in {self.json_file_path}. Updating diagram...")
                    self.update_diagram_full()
                time.sleep(self.poll_interval)

        Thread(target=run, daemon=True).start()

    def stop(self):
        self.running = False
        print("DiagramUpdater stopped.")


if __name__ == "__main__":
    json_file = "demo-jvm/build/knit.json"
    output_file = "demo-jvm/build/knit_diagram.mmd"
    updater = DiagramUpdater(json_file, output_file, direction="TD", poll_interval=2)
    try:
        updater.start()
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        updater.stop()