import React, { useEffect, useRef } from "react";
import * as d3 from "d3";
import { createRoot } from "react-dom/client";
import type { AdjacencyList } from "../knit/interfaces";

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
        d.isSourceError ? "red" :
        d.hasUpstreamError ? "orange" :
        d.isOptimistic ? "lightblue" : "gray"
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

// --- Mount into the webview and wait for data from the extension ---
const container = document.getElementById("app");
if (container) {
  const root = createRoot(container);
  const render = (payload: AdjacencyList) => {
    root.render(<Graph data={payload} />);
  };

  // Listen for messages from the extension
  window.addEventListener('message', (event: MessageEvent) => {
    const msg = event.data;
    if (msg?.type === 'graph-data' && msg?.payload) {
      render(msg.payload as AdjacencyList);
      console.log('Graph data received', msg.payload);
    }
  });

  console.log("graph webview ready");
} else {
  console.error("No #app container found");
}
