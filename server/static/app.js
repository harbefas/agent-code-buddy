const pendingEl = document.querySelector('#pending');
const historyEl = document.querySelector('#history');
const statusEl = document.querySelector('#status');
const spriteEl = document.querySelector('#buddy-sprite');
let lastPending = new Set();
let wasConnected = false;
let lastHadPending = false;
let moodTimer = null;
let currentMood = 'sleep';
let frame = 0;

const PET = {
  sleep: [
    [
      '       z z        ',
      '    .[--].        ',
      '   [ -  - ]       ',
      '   [ ____ ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '        Z         ',
      '    .[..].        ',
      '   [ .  . ]       ',
      '   [ ____ ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '                  ',
      '    .[  ].        ',
      '   [      ]       ',
      '   [ ____ ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '       z z        ',
      '    .[--].        ',
      '   [ -  - ]       ',
      '   [ ____ ]       ',
      '    `----`        ',
      '  ------------    '
    ]
  ],
  idle: [
    [
      '      [*]         ',
      '    .[||].        ',
      '   [ o  o ]       ',
      '   [  ==  ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '                  ',
      '    .[||].        ',
      '   [o   o ]       ',
      '   [  ==  ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '                  ',
      '    .[||].        ',
      '   [ o   o]       ',
      '   [  ==  ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '                  ',
      '    .[||].        ',
      '   [ -  - ]       ',
      '   [  ==  ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '                  ',
      '    .[||].        ',
      '   [ o  o ]       ',
      '   [  ==  ]       ',
      '    `----`        ',
      '  ------------    '
    ]
  ],
  pending: [
    [
      '     [!!!]        ',
      '    .[!!].        ',
      '   [ O  O ]       ',
      '   [ #### ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '     {!!!}        ',
      '    .[!!].        ',
      '   [O    O]       ',
      '   [ #### ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '     [???]        ',
      '    .[||].        ',
      '   [ O  O ]       ',
      '   [ #### ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '     [!!!]        ',
      '    .[!!].        ',
      '   [ O  O ]       ',
      '   [ #### ]       ',
      '    =`----`=      ',
      '  ------------    '
    ]
  ],
  allow: [
    [
      '    *[OK]*        ',
      '    .[**].        ',
      '   [ ^  ^ ]       ',
      '   [ ^^^^ ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '   * [++] *       ',
      '    .[**].        ',
      '   [ ^  ^ ]       ',
      '   [  OK  ]       ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '    + [*] +       ',
      '    .[||].        ',
      '   [ ^  ^ ]       ',
      '   [ ==== ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '    *[OK]*        ',
      '    .[**].        ',
      '   [ ^  ^ ]       ',
      '   [ ^^^^ ]       ',
      '    =`----`=      ',
      '  ------------    '
    ]
  ],
  reject: [
    [
      '     [NO]         ',
      '    .[xx].        ',
      '   [ x  x ]       ',
      '   [ ~~~~ ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '     [ERR]        ',
      '    .[!!].        ',
      '   [ X  X ]       ',
      '   [ #v# ]        ',
      '    `----`        ',
      '  ------------    '
    ],
    [
      '     [NO]         ',
      '    .[xx].        ',
      '   [ x  x ]       ',
      '   [ ____ ]       ',
      '    =`----`=      ',
      '  ------------    '
    ],
    [
      '     [NO]         ',
      '    .[xx].        ',
      '   [ x  x ]       ',
      '   [ ~~~~ ]       ',
      '    =`----`=      ',
      '  ------------    '
    ]
  ]
};

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"]/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
  }[ch]));
}

function label(decision) {
  return {
    allow_once: 'Allowed once',
    always_allow: 'Always allowed',
    reject: 'Rejected',
    timeout: 'Timed out'
  }[decision] || 'Pending';
}

async function decide(id, decision) {
  setMood(decision === 'reject' ? 'reject' : 'allow', 14000);
  await fetch(`/api/decide/${id}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ decision })
  });
  await loadState();
}

function setMood(nextMood, ttl = 0) {
  if (moodTimer) clearTimeout(moodTimer);
  currentMood = nextMood;
  document.body.dataset.mood = nextMood;
  if (ttl > 0) {
    moodTimer = setTimeout(() => {
      setMood('idle');
    }, ttl);
  }
}

function drawBuddy() {
  const state = PET[currentMood] ? currentMood : 'idle';
  const frames = PET[state] || PET.idle;
  const art = frames[frame % frames.length];
  const width = Math.max(...art.map(line => line.length));
  spriteEl.textContent = art.map(line => line.padEnd(width, ' ')).join('\n');
  spriteEl.dataset.state = state;
  frame += 1;
}

function card(item) {
  const isPending = item.status === 'pending';
  const detail = item.detail || '(no detail)';
  const decisionClass = item.decision || 'pending';
  return `
    <article class="card ${isPending ? 'pending' : ''}">
      <div class="meta">
        <span class="chip chip-agent">${escapeHtml(item.agent)}</span>
        <span class="chip chip-tool">${escapeHtml(item.tool)}</span>
        <span class="chip">${new Date(item.created_at).toLocaleTimeString()}</span>
      </div>
      <div class="title">${escapeHtml(item.title)}</div>
      <pre class="detail">${escapeHtml(detail)}</pre>
      ${isPending ? `
        <div class="actions">
          <button class="action-allow" data-id="${item.id}" data-decision="allow_once">Allow</button>
          <button class="action-always" data-id="${item.id}" data-decision="always_allow">Trust</button>
          <button class="action-reject" data-id="${item.id}" data-decision="reject">Deny</button>
        </div>` : `<div class="meta"><span class="chip chip-decision ${escapeHtml(decisionClass)}">${label(item.decision)}</span></div>`}
    </article>`;
}

function saveStateToCache(requests) {
  try {
    localStorage.setItem('buddy_requests', JSON.stringify(requests));
    localStorage.setItem('buddy_last_update', Date.now().toString());
  } catch (e) {
  }
}

function loadStateFromCache() {
  try {
    const raw = localStorage.getItem('buddy_requests');
    return raw ? JSON.parse(raw) : [];
  } catch (e) {
    return [];
  }
}

function renderState(requests) {
  const pending = requests.filter(item => item.status === 'pending').reverse();
  const history = requests.filter(item => item.status !== 'pending').reverse().slice(0, 20);
  const newest = requests[requests.length - 1];

  const pendingIds = new Set(pending.map(item => item.id));
  for (const id of pendingIds) {
    if (!lastPending.has(id) && navigator.vibrate) navigator.vibrate([80, 40, 80]);
  }
  lastPending = pendingIds;

  if (pending.length) {
    setMood('pending');
  } else if (newest && newest.status === 'decided' && Date.now() - newest.decided_at < 15000) {
    setMood(newest.decision === 'reject' || newest.decision === 'timeout' ? 'reject' : 'allow', 15000);
  } else {
    setMood('idle');
  }

  document.body.dataset.hasPending = pending.length ? 'true' : 'false';
  pendingEl.innerHTML = pending.map(card).join('');
  historyEl.innerHTML = history.map(card).join('') || '<p class="empty">No decisions yet</p>';
  lastHadPending = pending.length > 0;
}

async function loadState() {
  try {
    const response = await fetch('/api/state', { cache: 'no-store' });
    const data = await response.json();
    const requests = data.requests || [];
    saveStateToCache(requests);
    renderState(requests);
    wasConnected = true;
    statusEl.textContent = pending.length ? `${pending.length} pending` : 'Connected';
  } catch (error) {
    const cached = loadStateFromCache();
    if (cached.length > 0) {
      renderState(cached);
      const lastUpdate = localStorage.getItem('buddy_last_update');
      const time = lastUpdate ? new Date(parseInt(lastUpdate)).toLocaleTimeString() : 'unknown';
      statusEl.textContent = `Offline (last update: ${time})`;
      wasConnected = true;
    } else {
      statusEl.textContent = wasConnected && !lastHadPending ? 'Session closed' : 'Disconnected';
      document.body.dataset.hasPending = 'false';
      setMood(wasConnected ? 'idle' : 'sleep');
    }
  }
}

pendingEl.addEventListener('click', event => {
  const button = event.target.closest('button[data-id]');
  if (!button) return;
  decide(button.dataset.id, button.dataset.decision);
});
setInterval(drawBuddy, 260);
setInterval(loadState, 1000);
drawBuddy();
loadState();
