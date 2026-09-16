const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const source = fs.readFileSync(path.join(__dirname, '../src/components/AppointmentActions.tsx'), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: {
  module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
} }).outputText;
const compiled = { exports: {} };
new Function('require', 'module', 'exports', code)(require, compiled, compiled.exports);
const Actions = compiled.exports.default;
const render = (status, manager = true, props = {}) => renderToStaticMarkup(React.createElement(Actions, {
  appointment: { id: 1, status, date: '2030-01-07', time: '09:00:00', endTime: '09:30:00' },
  manager, busy: false, today: '2030-01-07', onReschedule() {}, onFollowUp() {}, onStatus() {}, ...props,
}));

test('reception sees check-in, consultation, check-out and follow-up in sequence', () => {
  assert.match(render('BOOKED'), />Check in</);
  assert.doesNotMatch(render('BOOKED'), />Check out<|>Book follow-up</);
  assert.match(render('ARRIVED'), />Start consultation</);
  assert.doesNotMatch(render('ARRIVED'), />Check out</);
  assert.match(render('IN_CONSULTATION'), />Check out</);
  assert.doesNotMatch(render('IN_CONSULTATION'), />Cancel appointment</);
  assert.match(render('COMPLETED'), />Book follow-up</);
  assert.doesNotMatch(render('COMPLETED'), />Check in<|>Check out</);
});

test('doctors can start consultations but reception owns check-in, check-out and follow-up', () => {
  assert.doesNotMatch(render('BOOKED', false), />Check in</);
  assert.match(render('ARRIVED', false), />Start consultation</);
  assert.doesNotMatch(render('IN_CONSULTATION', false), /<button/);
  assert.doesNotMatch(render('COMPLETED', false), />Book follow-up</);
});

test('future visits cannot be checked in or started; terminal cancellations have no actions', () => {
  assert.doesNotMatch(render('BOOKED', true, {today:'2030-01-06'}), />Check in</);
  assert.doesNotMatch(render('ARRIVED', true, {today:'2030-01-06'}), />Start consultation</);
  assert.doesNotMatch(render('CANCELLED'), /<button/);
  assert.doesNotMatch(render('NO_SHOW'), /<button/);
  assert.match(render('IN_CONSULTATION', true, {busy:true}), /<button[^>]*disabled=""/);
});

test('follow-up and consultation buttons dispatch the selected appointment', () => {
  const appointment = {id:42,status:'COMPLETED'};
  let selected;
  const props = {appointment,manager:true,busy:false,today:'2030-01-07',onFollowUp:a=>{selected=a;}};
  const tree = Actions(props);
  const button = React.Children.toArray(tree.props.children).find(child=>child.props?.children==='Book follow-up');
  button.props.onClick();
  assert.equal(selected, appointment);
  let change;
  const waiting = {...appointment,status:'ARRIVED',date:'2030-01-07'};
  const startTree = Actions({...props,appointment:waiting,onStatus:(a,status)=>{change={a,status};}});
  React.Children.toArray(startTree.props.children).find(child=>child.props?.children==='Start consultation').props.onClick();
  assert.deepEqual(change,{a:waiting,status:'IN_CONSULTATION'});
});
