import React, { useEffect, useRef } from "react";
import * as d3 from "d3";
import { createRoot } from "react-dom/client";

export interface Node {
  name: string;
  is_optimistic: boolean;
  is_source_error: boolean;
  error_message?: string;
  is_in_last_update: boolean;
  has_upstream_error: boolean;
}

export interface AdjacencyList {
  nodes: Record<string, Node>;
  edges: [string, string][];
}

interface GraphProps { data: AdjacencyList; }

const Graph: React.FC<GraphProps> = ({ data }) => {
  const svgRef = useRef<SVGSVGElement | null>(null);

  useEffect(() => {
    if (!data || !svgRef.current) {return;}

    const width = 600;
    const height = 400;

    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const nodes = Object.values(data.nodes).map(n => ({ ...n }));
    const links = data.edges.map(([source, target]) => ({ source, target }));

    const simulation = d3
      .forceSimulation(nodes as d3.SimulationNodeDatum[])
      .force("link", d3.forceLink(links).id((d: any) => d.name).distance(100))
      .force("charge", d3.forceManyBody().strength(-200))
      .force("center", d3.forceCenter(width / 2, height / 2));

    svg.attr("width", width).attr("height", height);

    const link = svg.append("g")
      .attr("stroke", "#999").attr("stroke-opacity", 0.6)
      .selectAll("line").data(links).enter().append("line")
      .attr("stroke-width", 2);

    const node = svg.append("g")
      .attr("stroke", "#fff").attr("stroke-width", 1.5)
      .selectAll("circle").data(nodes).enter().append("circle")
      .attr("r", 12)
      .attr("fill", (d: any) =>
        d.is_source_error ? "red" :
        d.has_upstream_error ? "orange" :
        d.is_optimistic ? "lightblue" : "gray"
      )
      .call(
        d3.drag<SVGCircleElement, any>()
          .on("start", (event, d: any) => {
            if (!event.active) {simulation.alphaTarget(0.3).restart();}
            d.fx = d.x; d.fy = d.y;
          })
          .on("drag", (event, d: any) => { d.fx = event.x; d.fy = event.y; })
          .on("end", (event, d: any) => {
            if (!event.active) {simulation.alphaTarget(0);}
            d.fx = null; d.fy = null;
          })
      );

    const label = svg.append("g").selectAll("text")
      .data(nodes).enter().append("text")
      .text((d: any) => d.name)
      .attr("font-size", 12).attr("dx", 15).attr("dy", 4);

    simulation.on("tick", () => {
      link
        .attr("x1", (d: any) => d.source.x)
        .attr("y1", (d: any) => d.source.y)
        .attr("x2", (d: any) => d.target.x)
        .attr("y2", (d: any) => d.target.y);

      (svg.selectAll("circle") as any)
        .attr("cx", (d: any) => d.x)
        .attr("cy", (d: any) => d.y);

      label.attr("x", (d: any) => d.x).attr("y", (d: any) => d.y);
    });

    return () => {
      simulation.stop();
    };
  }, [data]);

  return <svg ref={svgRef}></svg>;
};

// --- Sample data so the panel shows something immediately ---
const sampleData: AdjacencyList = {
  nodes: {
    A: { name: "A", is_optimistic: false, is_source_error: false, is_in_last_update: true,  has_upstream_error: false },
    B: { name: "B", is_optimistic: true,  is_source_error: false, is_in_last_update: false, has_upstream_error: false },
    C: { name: "C", is_optimistic: false, is_source_error: true,  error_message: "Failed to load", is_in_last_update: false, has_upstream_error: false },
    D: { name: "D", is_optimistic: false, is_source_error: false, is_in_last_update: true,  has_upstream_error: true }
  },
  edges: [["A","B"], ["B","C"], ["A","D"]]
};

const GraphDemo: React.FC = () => <Graph data={sampleData} />;

// --- Mount into the webview ---
const container = document.getElementById("app");
if (container) {
  createRoot(container).render(<GraphDemo />);
  console.log("graph mounted");
} else {
  console.error("No #app container found");
}
