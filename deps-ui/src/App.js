import './App.css';
import Graph from 'react-graph-vis'
import { ReactTerminal, ReactThemes } from 'react-terminal-component'

function App() {
  return (
    <div className="App">
      <TerminalComponent />
      <GraphOfDependencies />
    </div>
  );
}

function TerminalComponent() {
  return (
    <div className="TerminalComponent">
      <ReactTerminal theme={{ ...ReactThemes.ember, height: "100%"}} />
    </div>
  )
}

function GraphOfDependencies() {
  const graph = {
    nodes: [
      { id: 1, label: "Node #1", title: "this is the first node" },
      { id: 2, label: "Node #2", title: "this is the second node" },
      { id: 3, label: "Node #3", title: "this is the third node" },
      { id: 4, label: "Node #4", title: "this is the fourth node" },
      { id: 5, label: "Node #5", title: "this is the fifth node" }
    ],
    edges: [
      { from: 3, to: 1 },
      { from: 1, to: 4 },
      { from: 4, to: 1 },
      { from: 1, to: 5 },
      { from: 5, to: 2 }
    ]
  }

  return <Graph graph={graph} />
}

export default App;
