import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';
import './shared/visuals/fluid/flow.css';
import './app/AppShell.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
