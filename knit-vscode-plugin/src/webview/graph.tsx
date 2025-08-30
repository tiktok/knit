"use client"

import type React from "react"
import { useEffect, useRef, useState } from "react"
import * as d3 from "d3"
import { createRoot } from "react-dom/client"

export interface Node {
  name: string
  is_optimistic: boolean
  is_source_error: boolean
  error_message?: string
  is_in_last_update: boolean
  has_upstream_error: boolean
}

export interface AdjacencyList {
  nodes: Record<string, Node>
  edges: [string, string][]
}

interface GraphProps {
  data: AdjacencyList
}

const Graph: React.FC<GraphProps> = ({ data }) => {
  const svgRef = useRef<SVGSVGElement | null>(null)
  const [selectedNodes, setSelectedNodes] = useState<Set<string>>(new Set())
  const [hoveredNode, setHoveredNode] = useState<string | null>(null)
  const [dimensions, setDimensions] = useState({ width: 800, height: 600 })

  const nodeRef = useRef<d3.Selection<SVGCircleElement, any, SVGGElement, unknown> | null>(null)
  const linkRef = useRef<d3.Selection<SVGLineElement, any, SVGGElement, unknown> | null>(null)
  const labelRef = useRef<d3.Selection<SVGTextElement, any, SVGGElement, unknown> | null>(null)

  useEffect(() => {
    const handleResize = () => {
      const container = document.getElementById("graph-container")
      if (container) {
        setDimensions({
          width: container.clientWidth,
          height: container.clientHeight,
        })
      }
    }

    window.addEventListener("resize", handleResize)
    handleResize()

    return () => window.removeEventListener("resize", handleResize)
  }, [])

  useEffect(() => {
    if (!data || !svgRef.current) return

    const { width, height } = dimensions
    const svg = d3.select(svgRef.current)
    svg.selectAll("*").remove()

    const zoom = d3
      .zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.1, 4])
      .on("zoom", (event) => {
        svg.select("g").attr("transform", event.transform)
      })

    svg.call(zoom)

    svg.on("click", (event) => {
      if (event.target === svg.node()) {
        setSelectedNodes(new Set())
      }
    })

    const container = svg.append("g")

    svg
      .append("defs")
      .selectAll("marker")
      .data(["normal", "selected", "dimmed"])
      .enter()
      .append("marker")
      .attr("id", (d) => `arrow-${d}`)
      .attr("viewBox", "0 -5 10 10")
      .attr("refX", 15) // Put arrows back at the end of lines
      .attr("refY", 0)
      .attr("markerWidth", 6)
      .attr("markerHeight", 6)
      .attr("orient", "auto")
      .append("path")
      .attr("d", "M0,-5L10,0L0,5")
      .attr("fill", (d) => {
        switch (d) {
          case "selected":
            return "#8b5cf6"
          case "dimmed":
            return "#6b7280"
          default:
            return "#9ca3af"
        }
      })

    svg.attr("width", width).attr("height", height)

    const nodes = Object.values(data.nodes).map((n) => ({ ...n }))
    const links = data.edges.map(([source, target]) => ({ source, target }))

    const simulation = d3
      .forceSimulation(nodes as d3.SimulationNodeDatum[])
      .force(
        "link",
        d3
          .forceLink(links)
          .id((d: any) => d.name)
          .distance(100),
      )
      .force("charge", d3.forceManyBody().strength(-300))
      .force("center", d3.forceCenter(width / 2, height / 2))
      .force("collision", d3.forceCollide().radius(30))

    const link = container
      .append("g")
      .attr("stroke", "#999")
      .attr("stroke-opacity", 0.6)
      .selectAll("line")
      .data(links)
      .enter()
      .append("line")
      .attr("stroke-width", 2)
      .attr("marker-end", "url(#arrow-normal)") // Use marker-end instead of marker-mid

    linkRef.current = link

    const node = container
      .append("g")
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5)
      .selectAll("circle")
      .data(nodes)
      .enter()
      .append("circle")
      .attr("r", 12)
      .attr("fill", (d: any) => {
        if (d.is_source_error) return "#dc2626"
        if (d.has_upstream_error) return "#f87171"
        if (d.is_optimistic) return "#10b981"
        return "#6b7280"
      })
      .style("cursor", "pointer")
      .on("mouseenter", (event, d: any) => {
        setHoveredNode(d.name)
      })
      .on("mouseleave", () => {
        setHoveredNode(null)
      })
      .on("click", (event, d: any) => {
        event.stopPropagation()
        const newSelected = new Set(selectedNodes)
        if (newSelected.has(d.name)) {
          newSelected.delete(d.name)
        } else {
          newSelected.add(d.name)
        }
        setSelectedNodes(newSelected)
      })
      .on("dblclick", (event, d: any) => {
        event.stopPropagation()
        console.log("[v0] Double-click detected on node:", d.name)

        const vscode = (window as any).vscode
        if (vscode) {
          vscode.postMessage({
            type: "navigateToNode",
            nodeId: d.name,
          })
          console.log("[v0] Message sent to VS Code for node:", d.name)
        } else {
          console.log("[v0] VS Code API not available")
        }
      })
      .call(
        d3
          .drag<SVGCircleElement, any>()
          .on("start", (event, d: any) => {
            if (!event.active) simulation.alphaTarget(0.3).restart()
            d.fx = d.x
            d.fy = d.y
          })
          .on("drag", (event, d: any) => {
            d.fx = event.x
            d.fy = event.y
          })
          .on("end", (event, d: any) => {
            if (!event.active) simulation.alphaTarget(0)
            d.fx = null
            d.fy = null
          }),
      )

    nodeRef.current = node

    const label = container
      .append("g")
      .selectAll("text")
      .data(nodes)
      .enter()
      .append("text")
      .text((d: any) => d.name)
      .attr("font-size", 12)
      .attr("fill", "#ffffff")
      .attr("dx", 15)
      .attr("dy", 4)
      .style("pointer-events", "none")

    labelRef.current = label

    simulation.on("tick", () => {
      link
        .attr("x1", (d: any) => d.source.x)
        .attr("y1", (d: any) => d.source.y)
        .attr("x2", (d: any) => d.target.x)
        .attr("y2", (d: any) => d.target.y)

      node.attr("cx", (d: any) => d.x).attr("cy", (d: any) => d.y)
      label.attr("x", (d: any) => d.x).attr("y", (d: any) => d.y)
    })

    return () => {
      simulation.stop()
    }
  }, [data, dimensions]) // Remove selectedNodes and hoveredNode from dependencies

  useEffect(() => {
    if (!nodeRef.current || !linkRef.current || !labelRef.current) return

    const hasSelection = selectedNodes.size > 0

    nodeRef.current
      .attr("r", (d: any) => (selectedNodes.has(d.name) ? 16 : 12))
      .attr("opacity", (d: any) => {
        if (!hasSelection) return 1
        return selectedNodes.has(d.name) ? 1 : 0.3
      })
      .attr("fill", (d: any) => {
        if (hoveredNode === d.name) return "#8b5cf6"
        if (selectedNodes.has(d.name)) return "#8b5cf6"
        if (d.is_source_error) return "#dc2626"
        if (d.has_upstream_error) return "#f87171"
        if (d.is_optimistic) return "#10b981"
        return "#6b7280"
      })

    linkRef.current
      .attr("opacity", (d: any) => {
        if (!hasSelection) return 0.6
        const sourceName = typeof d.source === "string" ? d.source : d.source.name
        return selectedNodes.has(sourceName) ? 1 : 0.2
      })
      .attr("stroke", (d: any) => {
        if (!hasSelection) return "#999"
        const sourceName = typeof d.source === "string" ? d.source : d.source.name
        return selectedNodes.has(sourceName) ? "#8b5cf6" : "#999"
      })
      .attr("marker-end", (d: any) => {
        if (!hasSelection) return "url(#arrow-normal)"
        const sourceName = typeof d.source === "string" ? d.source : d.source.name
        return selectedNodes.has(sourceName) ? "url(#arrow-selected)" : "url(#arrow-dimmed)"
      })

    labelRef.current.attr("opacity", (d: any) => {
      if (!hasSelection) return 1
      return selectedNodes.has(d.name) ? 1 : 0.5
    })
  }, [selectedNodes, hoveredNode]) // Only depend on visual state changes

  return (
    <div style={{ width: "100%", height: "100%", display: "flex", flexDirection: "column" }}>
      <div className="controls">
        <div className="control-group">
          <button onClick={() => setSelectedNodes(new Set())}>Clear Selection</button>
        </div>

        {selectedNodes.size > 0 && (
          <div className="selected-info">Selected: {Array.from(selectedNodes).join(", ")}</div>
        )}

        <div className="legend" style={{ color: "#000000" }}>
          <div className="legend-item">
            <div className="legend-color" style={{ backgroundColor: "#6b7280" }}></div>
            Normal
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{ backgroundColor: "#dc2626" }}></div>
            Source Error
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{ backgroundColor: "#f87171" }}></div>
            Upstream Error
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{ backgroundColor: "#10b981" }}></div>
            Optimistic
          </div>
          <div className="legend-item">
            <div className="legend-color" style={{ backgroundColor: "#8b5cf6" }}></div>
            Selected/Hover
          </div>
        </div>
      </div>

      <div id="graph-container" style={{ flex: 1 }}>
        <svg ref={svgRef} width="100%" height="100%"></svg>
      </div>
    </div>
  )
}

const sampleData: AdjacencyList = {
  nodes: {
    "main.ts": {
      name: "main.ts",
      is_optimistic: false,
      is_source_error: false,
      is_in_last_update: true,
      has_upstream_error: false,
    },
    "auth.ts": {
      name: "auth.ts",
      is_optimistic: true,
      is_source_error: false,
      is_in_last_update: false,
      has_upstream_error: false,
    },
    "database.ts": {
      name: "database.ts",
      is_optimistic: false,
      is_source_error: true,
      error_message: "Connection failed",
      is_in_last_update: false,
      has_upstream_error: false,
    },
    "api.ts": {
      name: "api.ts",
      is_optimistic: false,
      is_source_error: false,
      is_in_last_update: true,
      has_upstream_error: true,
    },
    "utils.ts": {
      name: "utils.ts",
      is_optimistic: false,
      is_source_error: false,
      is_in_last_update: true,
      has_upstream_error: false,
    },
    "config.ts": {
      name: "config.ts",
      is_optimistic: false,
      is_source_error: false,
      is_in_last_update: false,
      has_upstream_error: false,
    },
  },
  edges: [
    ["main.ts", "auth.ts"],
    ["main.ts", "api.ts"],
    ["auth.ts", "database.ts"],
    ["api.ts", "database.ts"],
    ["api.ts", "utils.ts"],
    ["utils.ts", "config.ts"],
  ],
}

const GraphDemo: React.FC = () => <Graph data={sampleData} />

const container = document.getElementById("app")
if (container) {
  createRoot(container).render(<GraphDemo />)
  console.log("Enhanced graph mounted successfully")
} else {
  console.error("No #app container found")
}
