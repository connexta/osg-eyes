import * as React from 'react'
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
            theme={ReactThemes.light} 
            autoFocus={false} 
            clickToFocus={true} />
        </div>
        <div className="App">
          <header className="App-header">
            <img src={logo} className="App-logo" alt="logo" />
            <p>
              Edit <code>src/App.js</code> and save to reload the page.
            </p>
            <a className="App-link" href="https://reactjs.org" target="_blank" rel="noopener noreferrer">
              Learn React
            </a>
          </header>
        </div>
      </div>
    );
  }
}

export default App;
