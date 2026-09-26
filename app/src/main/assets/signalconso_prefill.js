// Prefills the SignalConso "Démarchage abusif" wizard from window.__icPlan. It only selects options and
// fills empty fields, each at most once, and never clicks "Suivant", "Continuer" or the final submit:
// the user reviews every step and moves on.
(function () {
  if (window.__icPrefillInstalled) return;
  window.__icPrefillInstalled = true;
  var done = {};

  function norm(s) {
    return (s || '').replace(/[’]/g, "'").replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function pickRadio(title) {
    if (!title || done['radio:' + title]) return;
    var labels = document.querySelectorAll('label');
    for (var i = 0; i < labels.length; i++) {
      if (norm(labels[i].innerText).indexOf(norm(title)) !== 0) continue;
      var input = labels[i].htmlFor ? document.getElementById(labels[i].htmlFor) : labels[i].querySelector('input');
      if (input && !input.checked) labels[i].click();
      done['radio:' + title] = true;
      return;
    }
  }

  // React tracks the native value setter: assign through it, then emit input/change so its state follows.
  function setValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fillOnce(key, el, value) {
    if (!el || !value || done[key]) return;
    done[key] = true;
    setValue(el, value);
  }

  // Step 4 ("Vos coordonnées") also has an input[name=phone], for the user's own number:
  // only target the one labelled as the number that called.
  function callerPhoneInput() {
    var inputs = document.querySelectorAll('input[name="phone"]');
    for (var i = 0; i < inputs.length; i++) {
      var label = inputs[i].id && document.querySelector('label[for="' + inputs[i].id + '"]');
      if (label && norm(label.innerText).indexOf('vous ayant contacté') !== -1) return inputs[i];
    }
    return null;
  }

  function run() {
    var plan = window.__icPlan;
    if (!plan) return;
    pickRadio(plan.problem);
    pickRadio(plan.subcategory);
    fillOnce('phone', callerPhoneInput(), plan.phone);
    var dates = document.querySelectorAll('input[type="date"]');
    for (var i = 0; i < dates.length && plan.dates && i < plan.dates.length; i++) {
      fillOnce('date:' + i, dates[i], plan.dates[i]);
    }
    fillOnce('description', document.querySelector('textarea'), plan.description);
  }

  new MutationObserver(run).observe(document.documentElement, { childList: true, subtree: true });
  run();
})();
