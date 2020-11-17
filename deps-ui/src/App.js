import * as React from 'react'
import XTermWrapper from './ConsoleXTerm.js'
import logo from './logo.svg';
import './App.css';

class App extends React.Component {
  render() {
    return (
      <div className="App">
        <div>
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
        <div>
          {/* <Terminal/> */}
          <XTermWrapper/>
        </div>
      </div>
    );
  }
}

export default App;
