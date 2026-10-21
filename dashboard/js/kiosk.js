// Home HQ kiosk commands (kiosk/README.md, "Screen commands from the page"). In the kiosk APK
// these reach window.HomeHQ; in a browser they do nothing and App.kiosk.available is false.
// Screen changes, including a tap or wake alarm, arrive as App.on('kiosk', state).
(function () {
  'use strict';
  var bridge = window.HomeHQ;

  function call(name, arg) {
    if (!bridge) return false;
    return arg === undefined ? bridge[name]() : bridge[name](arg);
  }
  // wakeAt: a Date or epoch ms; omitted means no wake alarm.
  function screenOff(name) {
    return function (wakeAt) { return call(name, wakeAt === undefined ? undefined : +wakeAt); };
  }

  App.kiosk = {
    available: !!bridge,
    sleep: screenOff('sleep'), // off if Home HQ is a device admin, otherwise dim
    dim: screenOff('dim'),     // always dim: the page keeps running, a tap wakes it
    wake: function () { return call('wake'); },
    setBrightness: function (level) { return call('setBrightness', level); }, // 0–1, -1 = system
    reload: function () { return call('reload'); },
    state: function () { return bridge ? bridge.state() : 'awake'; },
    canTurnOff: function () { return !!bridge && bridge.canTurnOff(); }
  };

  window.addEventListener('homehq:state', function (e) { App.emit('kiosk', e.detail); });
})();
