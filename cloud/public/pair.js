'use strict';
// No automatic claim from a browser or link preview; fragments stay client-side.
document.getElementById('notice').textContent=location.hash?'An invitation is present. It expires after five minutes and requires administrator approval.':'No invitation supplied.';
