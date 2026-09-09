import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

function App() {
  return (
    <main className="page">
      <section className="card">
        <h1>Homeopathy Clinic</h1>
        <p>Cloud development environment is ready.</p>
        <div className="status">Frontend: Running</div>
        <p className="next">Next: patient registration and appointment scheduling.</p>
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode><App /></React.StrictMode>
);
