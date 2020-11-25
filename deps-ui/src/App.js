import * as React from 'react'
import Graph from 'react-graph-vis'
import { ReactTerminalStateless, ReactThemes } from 'react-terminal-component';
import {Emulator, EmulatorState} from 'javascript-terminal'
import Immutable from 'immutable'
//import XTermWrapper from './ConsoleXTerm.js'
import logo from './logo.svg';
import './App.css';

class ClojureEmulator extends Emulator {
  constructor(handleExecute) {
    super()
    this.handleExecute = handleExecute
  }

  execute(state, str, executionListeners = [], errorStr) {
    const result = super.execute(state, str, executionListeners, errorStr)
    return this.handleExecute(result)
  }
}

class ClojureReactTerminal extends ReactTerminalStateless {
  constructor(props) {
    super({emulatorState: EmulatorState.createEmpty()});
    this.emulator = new ClojureEmulator(this._handleExecute);
  }

  _stateWithNewOuts = (old, outs) => {
    return EmulatorState.create({
      'fs': old.getFileSystem(),
      'environmentVariables': old.getEnvVariables(),
      'history': old.getHistory(),
      'outputs': outs,
      'commandMapping': old.getCommandMapping()
    })
  }

  // I think it's possible to place the terminal in a 'waiting' state
  // and use the callback to actually print the result, and make the 
  // console interactive again
  _handleExecute = (result) => {
    this._doEval()
    return this._stateWithNewOuts(result, result.getOutputs().butLast())
  }

  _doEval = () => {
    window.clojure.eval(this.props.inputStr, (r) => {
      const isError = (r instanceof Array)
      const newOuts = this.props.emulatorState.getOutputs().push(Immutable.Map({
        type: isError ? 'TEXT_ERROR_OUTPUT' : 'TEXT_OUTPUT',
        content: isError ? 'Error: ' + r[0] : r
      }))
      this.props.onStateChange(this._stateWithNewOuts(this.props.emulatorState, newOuts))
    })
  }
}

class App extends React.Component {
  constructor() {
    super()
    this.state = {
      inputStr: '(+ 1 1)',
      emulatorState: EmulatorState.createEmpty()
    }
    window.printState = () => {
      console.log({
        inputStr: this.state.inputStr,
        emulatorState: (this.state.emulatorState !== undefined) ? this.state.emulatorState.toJS() : null
      })
    }
  }

  _init(props) {
    const {emulatorState, inputStr} = props;
    this.setState({
      emulatorState,
      inputStr
    });
  }

  _onInputChange = (inputStr) => {
    this.setState({inputStr});
  }

  _onStateChange = (emulatorState) => {
    this.setState({emulatorState, inputStr: ''});
  }

  componentDidMount() {
    this._init(this.props);
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps) {
      this._init(nextProps);
    }
  }

  render() {
    const graph = {
      nodes: [
        { id: 1, label: "Node 1", color: "#e04141" },
        { id: 2, label: "Node 2", color: "#e09c41" },
        { id: 3, label: "Node 3", color: "#e0df41" },
        { id: 4, label: "Node 4", color: "#7be041" },
        { id: 5, label: "Node 5", color: "#41e0c9" }
      ],
      edges: [{ id: '1v', from: 1, to: 2 }, { id: '2v', from: 1, to: 3 }, { id: '3v', from: 2, to: 4 }, { id: '4v', from: 2, to: 5 }]
    }
    const options = {
      layout: {
        hierarchical: false
      },
      edges: {
        color: "#000000"
      }
    }
    const events = {
      select: function(event) {
        var { nodes, edges } = event;
        console.log("Selected nodes:");
        console.log(nodes);
        console.log("Selected edges:");
        console.log(edges);
      }
    }
    return (
      <div>
        <div className="Terminal">
          {/* <Terminal/> */}
          {/* <XTermWrapper/> */}
          <ClojureReactTerminal 
            // State
            inputStr={this.state.inputStr}
            emulatorState={this.state.emulatorState}
            // Callback
            onInputChange={this._onInputChange}
            onStateChange={this._onStateChange}
            // Config  
            theme={ReactThemes.ember} 
            autoFocus={false} 
            clickToFocus={true} />
        </div>
        <div className="App">
          <div className="Viewer">
            <Graph key="graph" graph={graph} options={options} events={events} style={{ height: "100vh"}} />
          </div>
          {/* <header className="App-header">
            <img src={logo} className="App-logo" alt="logo" />
            <p>
              Edit <code>src/App.js</code> and save to reload the page.
            </p>
            <a className="App-link" href="https://reactjs.org" target="_blank" rel="noopener noreferrer">
              Learn React
            </a>
          </header> */}
        </div>
      </div>
    );
  }
}

export default App;
