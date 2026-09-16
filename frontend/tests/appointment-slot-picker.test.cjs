const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

// Compile the actual component for Node; CSS is not needed for server rendering.
const source = fs.readFileSync(path.join(__dirname, '../src/components/AppointmentSlotPicker.tsx'), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
} }).outputText;
const compiled = { exports: {} };
new Function('require', 'module', 'exports', code)(
  name => name.endsWith('.css') ? {} : require(name), compiled, compiled.exports);
const Picker = compiled.exports.default;
const slots = [{ time: '09:00:00', available: true }, { time: '09:30:00', available: false }];
const render = (props = {}) => renderToStaticMarkup(React.createElement(Picker, {
  slots, time: '', loading: false, error: '', ready: true, onChange() {}, ...props,
}));

test('available slots are selectable radios; unavailable slots remain visible and disabled', () => {
  const html = render();
  const inputs = html.match(/<input[^>]*>/g);
  assert.equal(inputs.length, 2);
  assert.doesNotMatch(inputs[0], /disabled/);
  assert.match(inputs[1], /disabled/);
  assert.match(html, /09:30/);
  assert.match(html, /Unavailable/);
  assert.doesNotMatch(html, /<select/);
});

test('selected time has a checked radio and a visible selection label', () => {
  const html = render({ time: '09:00:00' });
  assert.match(html, /<input[^>]*checked=""[^>]*value="09:00:00"/);
  assert.match(html, /Selected time:/);
  assert.match(html, /is-selected/);
  assert.doesNotMatch(render(), /checked=""/);
});

test('loading and errors hide stale slots', () => {
  assert.doesNotMatch(render({ loading: true }), /<input/);
  assert.match(render({ loading: true }), /Checking availability/);
  assert.doesNotMatch(render({ error: 'Connection failed' }), /<input/);
  assert.match(render({ error: 'Connection failed' }), /role="alert">Connection failed/);
});

test('fully booked days keep the unavailable grid; closed days explain the empty result', () => {
  const html = render({ slots: slots.map(slot => ({ ...slot, available: false })) });
  assert.equal((html.match(/<input[^>]*disabled/g) || []).length, 2);
  assert.match(html, /No available slots/);
  assert.match(render({ slots: [] }), /No working hours/);
  assert.match(render({ slots: [], ready: false }), /Select a doctor and date/);
});
