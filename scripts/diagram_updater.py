import os
import json
import time
from threading import Thread
from adjacency_list import AdjacencyList
from visualiser import Visualiser


class DiagramUpdater:
    def __init__(self, json_file_path, output_file_path, direction="TD", poll_interval=2):
        """
        Initialize the DiagramUpdater.

        :param json_file_path: Path to the JSON file to monitor.
        :param output_file_path: Path to the output Mermaid diagram file.
        :param direction: Direction of the Mermaid diagram (e.g., "TD", "LR").
        :param poll_interval: Time interval (in seconds) to poll for changes.
        """
        self.json_file_path = json_file_path
        self.output_file_path = output_file_path
        self.direction = direction
        self.poll_interval = poll_interval
        self.last_modified_time = None
        self.running = False
        self.adjacency_list = AdjacencyList()
        self.visualiser = Visualiser()

    def check_for_changes(self):
        """
        Check if the JSON file has been modified since the last check.
        """
        try:
            current_modified_time = os.path.getmtime(self.json_file_path)
            if self.last_modified_time is None or current_modified_time > self.last_modified_time:
                self.last_modified_time = current_modified_time
                return True
        except FileNotFoundError:
            print(f"ERROR: JSON file not found: {self.json_file_path}")
        return False

    def update_diagram(self):
        """
        Generate the Mermaid diagram from the JSON file.
        """
        try:
            # Read and parse the JSON file
            with open(self.json_file_path, 'r') as f:
                component_dumps = json.load(f)

            # Build the adjacency list
            adjacency_list = self.adjacency_list.build_adjacency_list(component_dumps)

            # Generate the Mermaid diagram
            mermaid_content = self.visualiser.build_mermaid_diagram(adjacency_list, self.direction)

            # Write the Mermaid diagram to the output file
            os.makedirs(os.path.dirname(self.output_file_path), exist_ok=True)
            with open(self.output_file_path, 'w') as f:
                f.write(mermaid_content)

            print(f"Updated Mermaid diagram at: {self.output_file_path}")
        except Exception as e:
            print(f"ERROR: Failed to update Mermaid diagram: {str(e)}")

    def start(self):
        """
        Start the DiagramUpdater session.
        """
        if self.running:
            print("DiagramUpdater is already running.")
            return

        self.running = True
        print(f"Starting DiagramUpdater for {self.json_file_path}...")

        def run():
            while self.running:
                if self.check_for_changes():
                    print(f"Changes detected in {self.json_file_path}. Updating diagram...")
                    self.update_diagram()
                time.sleep(self.poll_interval)

        Thread(target=run, daemon=True).start()

    def stop(self):
        """
        Stop the DiagramUpdater session.
        """
        self.running = False
        print("DiagramUpdater stopped.")


# Example usage
if __name__ == "__main__":
    json_file = "demo-jvm/build/knit.json"
    output_file = "demo-jvm/build/knit_diagram.mmd"

    updater = DiagramUpdater(json_file, output_file, direction="TD", poll_interval=2)
    try:
        updater.start()
        while True:
            time.sleep(1)  # Keep the main thread alive
    except KeyboardInterrupt:
        updater.stop()