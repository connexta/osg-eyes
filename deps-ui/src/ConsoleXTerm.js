import * as React from 'react'
import { Terminal } from 'xterm';
//import "xterm/css/xterm.css";
import "xterm/dist/xterm.css";

const className = require('classnames');

// Upgrade XTerm later - "xterm": "^4.9.0",
class XTerm extends React.Component {
    xterm;
    container;
  
    constructor(props, context) {
      super(props, context);
      this.state = {
          isFocused: false
      };
    }
  
    applyAddon(addon) {
      Terminal.applyAddon(addon);
    }
  
    componentDidMount() {
      if (this.props.addons) {
        this.props.addons.forEach(s => {
            const addon = require(`xterm/dist/addons/${s}/${s}.js`);
            Terminal.applyAddon(addon);
        });
      }
  
      this.xterm = new Terminal(this.props.options);
      this.xterm.open(this.container);
      this.xterm.on('focus', this.focusChanged.bind(this, true));
      this.xterm.on('blur', this.focusChanged.bind(this, false));
  
      if (this.props.onContextMenu) {
          this.xterm.element.addEventListener('contextmenu', this.onContextMenu.bind(this));
      }
      if (this.props.onInput) {
          this.xterm.on('data', this.onInput);
      }
      if (this.props.value) {
          this.xterm.write(this.props.value);
      }
    }
  
    componentWillUnmount() {
      // is there a lighter-weight way to remove the cm instance?
      if (this.xterm) {
        this.xterm.destroy();
        this.xterm = null;
      }
    }
  
    shouldComponentUpdate(nextProps, nextState) {
      // console.log('shouldComponentUpdate', nextProps.hasOwnProperty('value'), nextProps.value != this.props.value);
      if (nextProps.hasOwnProperty('value') && nextProps.value != this.props.value) {
        if (this.xterm) {
          this.xterm.clear();
          setTimeout(() => {
            this.xterm.write(nextProps.value);
          }, 0)
        }
      }
      return false;
    }
  
    getTerminal() {
      return this.xterm;
    }
  
    write(data) {
      this.xterm && this.xterm.write(data);
    }
  
    writeln(data) {
      this.xterm && this.xterm.writeln(data);
    }
  
    focus() {
      if (this.xterm) {
          this.xterm.focus();
      }
    }
  
    focusChanged(focused) {
      this.setState({
          isFocused: focused
      });
      this.props.onFocusChange && this.props.onFocusChange(focused);
    }
  
    onInput = data => {
      this.props.onInput && this.props.onInput(data);
    };
  
    resize(cols, rows) {
      this.xterm && this.xterm.resize(Math.round(cols), Math.round(rows));
    }
  
    setOption(key, value) {
      this.xterm && this.xterm.setOption(key, value);
    }
  
    refresh() {
      this.xterm && this.xterm.refresh(0, this.xterm.rows - 1);
    }
  
    onContextMenu(e) {
      this.props.onContextMenu && this.props.onContextMenu(e);
    }
  
    render() {
      const terminalClassName = className('ReactXTerm', this.state.isFocused ? 'ReactXTerm--focused' : null, this.props.className);
      return <div ref={ref => (this.container = ref)} className={terminalClassName} />;
    }
  }

  class XTermWrapper extends React.Component {
    inputRef;

    constructor(props, context) {
        super(props, context)
        this.inputRef = React.createRef()
    }

    componentDidMount() {
        //runFakeTerminal(this.inputRef.current!!);
        this.runFakeTerminal(this.inputRef.current);
    }
    
    componentWillUnmount() {
        this.inputRef.current?.componentWillUnmount();
    }

    render() {
        return (
            <XTerm ref={this.inputRef}
                addons={['fit', 'fullscreen', 'search']}
                style={{
                  overflow: 'hidden',
                  position: 'relative',
                  width: '100%',
                  height: '100%'
                }}/>
        )
    }

    runFakeTerminal(xterm) {
        const term = xterm.getTerminal();
        const shellprompt = '$ ';
    
        function prompt () {
          xterm.write('\r\n' + shellprompt);
        }
    
        xterm.writeln('Welcome to xterm.js');
        xterm.writeln('This is a local terminal emulation, without a real terminal in the back-end.');
        xterm.writeln('Type some keys and commands to play around.');
        xterm.writeln('');
        prompt();
    
        term.on('key', function (key, ev) {
          var printable = (
            //!ev!!.altKey && !ev!!.ctrlKey && !ev!!.metaKey
            !ev.altKey && !ev.ctrlKey && !ev.metaKey
          );
    
          if (ev.keyCode == 13) {
          //if (ev!!.keyCode == 13) {
            prompt();
            // } else if (ev.keyCode == 8) {
            //   // Do not delete the prompt
            //   if (term['x'] > 2) {
            //     xterm.write('\b \b');
            //   }
          } else if (printable) {
            xterm.write(key);
          }
        });
    
        term.on('paste', function (data, ev) {
          xterm.write(data);
        });
    }
  }

  export default XTermWrapper